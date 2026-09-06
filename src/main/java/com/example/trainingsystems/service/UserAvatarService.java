package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserAvatarEntity;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserAvatarRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
public class UserAvatarService {
    public static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final String PATIENT = "PATIENT";
    private static final String THERAPIST = "THERAPIST";

    private final UserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final UserBindingRepository bindingRepository;
    private final FriendshipRepository friendshipRepository;
    private final CustomExerciseIdentityService identityService;

    public UserAvatarService(
        UserRepository userRepository,
        UserAvatarRepository avatarRepository,
        UserBindingRepository bindingRepository,
        FriendshipRepository friendshipRepository,
        CustomExerciseIdentityService identityService
    ) {
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.bindingRepository = bindingRepository;
        this.friendshipRepository = friendshipRepository;
        this.identityService = identityService;
    }

    @Transactional
    public void uploadCurrentUserAvatar(
        Long userId,
        String identityToken,
        MultipartFile file
    ) {
        User user = requireCurrentUser(userId, identityToken);
        if (file == null || file.isEmpty()) {
            throw badRequest("AVATAR_EMPTY", "請選擇有效的圖片檔案");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw tooLarge();
        }

        String mimeType = normalizeMimeType(file.getContentType());
        byte[] imageData;
        try {
            imageData = file.getBytes();
        } catch (IOException error) {
            throw badRequest("AVATAR_READ_FAILED", "無法讀取選取的圖片");
        }
        if (imageData.length == 0) {
            throw badRequest("AVATAR_EMPTY", "請選擇有效的圖片檔案");
        }
        if (imageData.length > MAX_IMAGE_BYTES) {
            throw tooLarge();
        }
        if (!hasExpectedSignature(mimeType, imageData)) {
            throw unsupported("圖片內容與檔案格式不符");
        }

        UserAvatarEntity avatar = avatarRepository
            .findById(user.getId())
            .orElseGet(UserAvatarEntity::new);
        avatar.setUserId(user.getId());
        avatar.setMimeType(mimeType);
        avatar.setImageData(imageData);
        avatarRepository.save(avatar);
    }

    @Transactional(readOnly = true)
    public AvatarContent getUserAvatar(
        Long viewerUserId,
        String identityToken,
        Long targetUserId
    ) {
        User viewer = requireCurrentUser(viewerUserId, identityToken);
        if (targetUserId == null) {
            throw badRequest("INVALID_TARGET_USER", "targetUserId 不可為空");
        }
        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> notFound("使用者不存在"));
        if (!canReadAvatar(viewer, target)) {
            throw forbidden("你沒有權限讀取此使用者的頭像");
        }
        UserAvatarEntity avatar = avatarRepository.findById(targetUserId)
            .orElseThrow(() -> notFound("使用者尚未設定自訂頭像"));
        return new AvatarContent(
            avatar.getImageData().clone(),
            avatar.getMimeType()
        );
    }

    private User requireCurrentUser(Long userId, String identityToken) {
        if (userId == null) {
            throw unauthorized("缺少登入身份");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> unauthorized("登入身份無效"));
        if (identityToken == null || identityToken.isBlank()) {
            throw unauthorized("缺少 identity token");
        }
        if (!identityService.isConfigured()) {
            throw new AvatarApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AVATAR_IDENTITY_UNAVAILABLE",
                "Avatar identity 尚未設定"
            );
        }
        if (!identityService.isValid(user, identityToken)) {
            throw forbidden("Identity token 無效");
        }
        return user;
    }

    private boolean canReadAvatar(User viewer, User target) {
        if (viewer.getId().equals(target.getId())) {
            return true;
        }
        if (hasRole(viewer, PATIENT) && hasRole(target, THERAPIST)) {
            return hasTherapistBinding(viewer.getId(), target.getId());
        }
        if (hasRole(viewer, THERAPIST) && hasRole(target, PATIENT)) {
            return hasTherapistBinding(target.getId(), viewer.getId());
        }
        if (hasRole(viewer, PATIENT) && hasRole(target, PATIENT)) {
            long lowId = Math.min(viewer.getId(), target.getId());
            long highId = Math.max(viewer.getId(), target.getId());
            return friendshipRepository.existsByUserLowIdAndUserHighId(
                lowId,
                highId
            );
        }
        return false;
    }

    private boolean hasTherapistBinding(Long patientId, Long therapistId) {
        return bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patientId,
                therapistId,
                THERAPIST
            );
    }

    private boolean hasRole(User user, String role) {
        return user.getRole() != null && role.equalsIgnoreCase(user.getRole());
    }

    private String normalizeMimeType(String rawMimeType) {
        if (rawMimeType == null) {
            throw unsupported("只接受 JPEG、PNG 或 WEBP 圖片");
        }
        String mimeType = rawMimeType
            .split(";", 2)[0]
            .trim()
            .toLowerCase(Locale.ROOT);
        if (!mimeType.equals("image/jpeg") &&
            !mimeType.equals("image/png") &&
            !mimeType.equals("image/webp")) {
            throw unsupported("只接受 JPEG、PNG 或 WEBP 圖片");
        }
        return mimeType;
    }

    private boolean hasExpectedSignature(String mimeType, byte[] data) {
        return switch (mimeType) {
            case "image/jpeg" -> data.length >= 3 &&
                unsigned(data[0]) == 0xFF &&
                unsigned(data[1]) == 0xD8 &&
                unsigned(data[2]) == 0xFF;
            case "image/png" -> data.length >= 8 &&
                unsigned(data[0]) == 0x89 &&
                data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 &&
                data[4] == 0x0D && data[5] == 0x0A &&
                data[6] == 0x1A && data[7] == 0x0A;
            case "image/webp" -> data.length >= 12 &&
                data[0] == 'R' && data[1] == 'I' &&
                data[2] == 'F' && data[3] == 'F' &&
                data[8] == 'W' && data[9] == 'E' &&
                data[10] == 'B' && data[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private AvatarApiException unauthorized(String message) {
        return new AvatarApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    private AvatarApiException forbidden(String message) {
        return new AvatarApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private AvatarApiException badRequest(String code, String message) {
        return new AvatarApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private AvatarApiException unsupported(String message) {
        return new AvatarApiException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_AVATAR_TYPE",
            message
        );
    }

    private AvatarApiException tooLarge() {
        return new AvatarApiException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "AVATAR_TOO_LARGE",
            "頭像不可超過 5 MB"
        );
    }

    private AvatarApiException notFound(String message) {
        return new AvatarApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", message);
    }

    public record AvatarContent(byte[] bytes, String mimeType) {
    }
}
