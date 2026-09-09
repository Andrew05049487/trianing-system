package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.TrainingHistoryVideoEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.TrainingHistoryVideoRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class TrainingHistoryVideoService {
    private static final String THERAPIST = "THERAPIST";

    private final TrainingHistoryRepository historyRepository;
    private final TrainingHistoryVideoRepository videoRepository;
    private final UserRepository userRepository;
    private final UserBindingRepository bindingRepository;
    private final CustomExerciseIdentityService identityService;
    private final long maxVideoBytes;

    public TrainingHistoryVideoService(
        TrainingHistoryRepository historyRepository,
        TrainingHistoryVideoRepository videoRepository,
        UserRepository userRepository,
        UserBindingRepository bindingRepository,
        CustomExerciseIdentityService identityService,
        @Value("${training.history.video.max-bytes:104857600}")
        long maxVideoBytes
    ) {
        this.historyRepository = historyRepository;
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.bindingRepository = bindingRepository;
        this.identityService = identityService;
        this.maxVideoBytes = maxVideoBytes;
    }

    @Transactional
    public void upload(Long historyId, Long userId, MultipartFile file) {
        TrainingHistoryEntity history = requireHistory(historyId);
        if (userId == null) {
            throw badRequest("VIDEO_USER_REQUIRED", "userId 不可為空");
        }
        if (!history.getUser().getId().equals(userId)) {
            throw forbidden("影片只能綁定到該使用者自己的訓練紀錄");
        }
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw badRequest("VIDEO_EMPTY", "請選擇有效的影片檔案");
        }
        if (file.getSize() > maxVideoBytes) {
            throw tooLarge();
        }

        String contentType = normalizeVideoType(file.getContentType());
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException error) {
            throw badRequest("VIDEO_READ_FAILED", "無法讀取上傳的影片");
        }
        if (data.length == 0) {
            throw badRequest("VIDEO_EMPTY", "請選擇有效的影片檔案");
        }
        if (data.length > maxVideoBytes) {
            throw tooLarge();
        }

        TrainingHistoryVideoEntity video = videoRepository
            .findById(historyId)
            .orElseGet(TrainingHistoryVideoEntity::new);
        boolean isNew = video.getHistoryId() == null;
        LocalDateTime now = LocalDateTime.now();
        video.setHistoryId(historyId);
        video.setFileName(normalizeFileName(file.getOriginalFilename()));
        video.setContentType(contentType);
        video.setFileSize((long) data.length);
        video.setVideoData(data);
        if (isNew) {
            video.setCreatedAt(now);
            video.setUpdatedAt(null);
        } else {
            video.setUpdatedAt(now);
        }
        videoRepository.save(video);
    }

    @Transactional(readOnly = true)
    public VideoContent read(
        Long historyId,
        Long viewerUserId,
        String identityToken
    ) {
        User viewer = requireViewer(viewerUserId, identityToken);
        TrainingHistoryEntity history = requireHistory(historyId);
        Long ownerId = history.getUser().getId();
        if (!viewer.getId().equals(ownerId) && !isBoundTherapist(viewer, ownerId)) {
            throw forbidden("你沒有權限讀取這段患者訓練影片");
        }
        TrainingHistoryVideoEntity video = videoRepository.findById(historyId)
            .orElseThrow(() -> notFound("VIDEO_NOT_FOUND", "此訓練紀錄沒有影片"));
        return new VideoContent(
            video.getVideoData(),
            video.getContentType(),
            video.getFileName()
        );
    }

    private TrainingHistoryEntity requireHistory(Long historyId) {
        if (historyId == null) {
            throw badRequest("INVALID_HISTORY_ID", "historyId 不可為空");
        }
        return historyRepository.findById(historyId)
            .orElseThrow(() -> notFound(
                "TRAINING_HISTORY_NOT_FOUND",
                "找不到訓練歷史紀錄"
            ));
    }

    private User requireViewer(Long userId, String identityToken) {
        if (userId == null || identityToken == null || identityToken.isBlank()) {
            throw unauthorized("缺少登入身份或 identity token");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> unauthorized("登入身份無效"));
        if (!identityService.isConfigured()) {
            throw new TrainingHistoryApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "VIDEO_IDENTITY_UNAVAILABLE",
                "影片 identity 驗證尚未設定"
            );
        }
        if (!identityService.isValid(user, identityToken)) {
            throw forbidden("Identity token 無效");
        }
        return user;
    }

    private boolean isBoundTherapist(User viewer, Long patientId) {
        return viewer.getRole() != null &&
            THERAPIST.equalsIgnoreCase(viewer.getRole()) &&
            bindingRepository
                .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                    patientId,
                    viewer.getId(),
                    THERAPIST
                );
    }

    private String normalizeVideoType(String raw) {
        if (raw == null) {
            throw unsupported("只接受 video/* 影片檔案");
        }
        String normalized = raw.split(";", 2)[0]
            .trim()
            .toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("video/") || normalized.length() <= 6) {
            throw unsupported("只接受 video/* 影片檔案");
        }
        return normalized;
    }

    private String normalizeFileName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty()) return null;
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private TrainingHistoryApiException unauthorized(String message) {
        return new TrainingHistoryApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            message
        );
    }

    private TrainingHistoryApiException forbidden(String message) {
        return new TrainingHistoryApiException(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            message
        );
    }

    private TrainingHistoryApiException badRequest(String code, String message) {
        return new TrainingHistoryApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private TrainingHistoryApiException unsupported(String message) {
        return new TrainingHistoryApiException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_VIDEO_TYPE",
            message
        );
    }

    private TrainingHistoryApiException tooLarge() {
        return new TrainingHistoryApiException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "VIDEO_TOO_LARGE",
            "訓練影片超過允許大小"
        );
    }

    private TrainingHistoryApiException notFound(String code, String message) {
        return new TrainingHistoryApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public record VideoContent(byte[] bytes, String contentType, String fileName) {
    }
}
