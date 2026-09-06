package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.FriendRequestCreateDto;
import com.example.trainingsystems.dto.FriendRequestRespondDto;
import com.example.trainingsystems.entity.FriendRequest;
import com.example.trainingsystems.entity.Friendship;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.FriendRequestRepository;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FriendService {
    private static final String PATIENT = "PATIENT";
    private static final String PENDING = "PENDING";

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final CustomExerciseIdentityService identityService;

    public FriendService(
        UserRepository userRepository,
        FriendRequestRepository friendRequestRepository,
        FriendshipRepository friendshipRepository,
        CustomExerciseIdentityService identityService
    ) {
        this.userRepository = userRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
        this.identityService = identityService;
    }

    @Transactional
    public Map<String, Object> sendRequest(
        Long userId,
        String identityToken,
        FriendRequestCreateDto requestDto
    ) {
        User sender = requireCurrentPatient(userId, identityToken);
        if (requestDto == null || requestDto.getFriendCode() == null ||
            requestDto.getFriendCode().isBlank()) {
            throw badRequest("請輸入好友代碼");
        }

        String friendCode = requestDto
            .getFriendCode()
            .trim()
            .toUpperCase(Locale.ROOT);

        User receiver = userRepository
            .findByFriendCodeIgnoreCase(friendCode)
            .orElseThrow(() -> notFound("找不到此好友代碼"));

        if (sender.getId().equals(receiver.getId())) {
            throw badRequest("不能加自己為好友");
        }
        if (!hasRole(receiver, PATIENT)) {
            throw forbidden("只能加入病友為好友");
        }

        long lowId = Math.min(
            sender.getId(),
            receiver.getId()
        );

        long highId = Math.max(
            sender.getId(),
            receiver.getId()
        );

        if (friendshipRepository
            .existsByUserLowIdAndUserHighId(lowId, highId)) {
            throw conflict("你們已經是好友");
        }

        FriendRequest reverseRequest = friendRequestRepository
            .findBySenderIdAndReceiverId(
                receiver.getId(),
                sender.getId()
            )
            .orElse(null);

        if (reverseRequest != null && PENDING.equals(reverseRequest.getStatus())) {
            throw conflict(
                "對方已向你發送邀請，請到好友邀請中處理"
            );
        }

        FriendRequest friendRequest = friendRequestRepository
            .findBySenderIdAndReceiverId(
                sender.getId(),
                receiver.getId()
            )
            .orElse(null);

        if (friendRequest != null) {
            if (PENDING.equals(friendRequest.getStatus())) {
                throw conflict("好友邀請已經送出");
            }

            friendRequest.setStatus(PENDING);
            friendRequest.setCreatedAt(LocalDateTime.now());
            friendRequest.setRespondedAt(null);
        } else {
            friendRequest = new FriendRequest();
            friendRequest.setSender(sender);
            friendRequest.setReceiver(receiver);
            friendRequest.setStatus(PENDING);
            friendRequest.setCreatedAt(LocalDateTime.now());
        }

        FriendRequest savedRequest =
            friendRequestRepository.save(friendRequest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "好友邀請已送出");
        result.put("requestId", savedRequest.getId());
        result.put("receiverId", receiver.getId());
        result.put("receiverName", safeName(receiver));
        result.put("status", savedRequest.getStatus());

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPendingRequests(
        Long userId,
        String identityToken
    ) {
        User currentUser = requireCurrentPatient(userId, identityToken);
        return friendRequestRepository
            .findByReceiverIdAndStatusOrderByCreatedAtDesc(
                currentUser.getId(),
                PENDING
            )
            .stream()
            .map(this::toPendingRequest)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSentRequests(
        Long userId,
        String identityToken
    ) {
        User currentUser = requireCurrentPatient(userId, identityToken);
        return friendRequestRepository
            .findBySenderIdAndStatusOrderByCreatedAtDesc(
                currentUser.getId(),
                PENDING
            )
            .stream()
            .map(this::toSentRequest)
            .toList();
    }
    @Transactional
    public Map<String, Object> respondToRequest(
        Long userId,
        String identityToken,
        Long requestId,
        FriendRequestRespondDto responseDto
    ) {
        User currentUser = requireCurrentPatient(userId, identityToken);
        if (responseDto == null || responseDto.getAction() == null ||
            responseDto.getAction().isBlank()) {
            throw badRequest("缺少 action");
        }

        FriendRequest friendRequest = friendRequestRepository
            .findById(requestId)
            .orElseThrow(() -> notFound("找不到好友邀請"));

        if (!friendRequest
            .getReceiver()
            .getId()
            .equals(currentUser.getId())) {
            throw forbidden("你沒有權限處理這個邀請");
        }

        if (!hasRole(friendRequest.getSender(), PATIENT)) {
            throw forbidden("好友功能僅限病患使用");
        }

        if (!PENDING.equals(friendRequest.getStatus())) {
            throw conflict("這個邀請已處理");
        }

        String action = responseDto
            .getAction()
            .trim()
            .toUpperCase(Locale.ROOT);

        if ("ACCEPT".equals(action)) {
            User sender = friendRequest.getSender();
            User receiver = friendRequest.getReceiver();

            long lowId = Math.min(
                sender.getId(),
                receiver.getId()
            );

            long highId = Math.max(
                sender.getId(),
                receiver.getId()
            );

            if (!friendshipRepository
                .existsByUserLowIdAndUserHighId(
                    lowId,
                    highId
                )) {

                User lowUser = sender.getId() == lowId
                    ? sender
                    : receiver;

                User highUser = sender.getId() == highId
                    ? sender
                    : receiver;

                Friendship friendship = new Friendship();
                friendship.setUserLow(lowUser);
                friendship.setUserHigh(highUser);
                friendship.setCreatedAt(
                    LocalDateTime.now()
                );

                friendshipRepository.save(friendship);
            }

            friendRequest.setStatus("ACCEPTED");

        } else if ("REJECT".equals(action)) {
            friendRequest.setStatus("REJECTED");

        } else {
            throw badRequest("action 只能是 ACCEPT 或 REJECT");
        }

        friendRequest.setRespondedAt(
            LocalDateTime.now()
        );

        friendRequestRepository.save(friendRequest);

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put(
            "message",
            "ACCEPT".equals(action)
                ? "已接受好友邀請"
                : "已拒絕好友邀請"
        );

        result.put(
            "requestId",
            friendRequest.getId()
        );

        result.put(
            "status",
            friendRequest.getStatus()
        );

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFriends(
        Long userId,
        String identityToken
    ) {
        User currentUser = requireCurrentPatient(userId, identityToken);

        return friendshipRepository
            .findByUserLowIdOrUserHighId(
                currentUser.getId(),
                currentUser.getId()
            )
            .stream()
            .filter(friendship -> hasRole(
                friendship.getUserLow().getId().equals(currentUser.getId())
                    ? friendship.getUserHigh()
                    : friendship.getUserLow(),
                PATIENT
            ))
            .map(friendship -> {
                User friend = friendship
                    .getUserLow()
                    .getId()
                    .equals(currentUser.getId())
                        ? friendship.getUserHigh()
                        : friendship.getUserLow();

                Map<String, Object> item =
                    new LinkedHashMap<>();

                item.put(
                    "friendshipId",
                    friendship.getId()
                );

                item.put(
                    "friendId",
                    friend.getId()
                );

                item.put(
                    "friendName",
                    safeName(friend)
                );

                item.put(
                    "friendCode",
                    friend.getFriendCode()
                );

                item.put(
                    "createdAt",
                    friendship.getCreatedAt()
                );

                return item;
            })
            .toList();
    }

    @Transactional
    public Map<String, Object> cancelRequest(
        Long userId,
        String identityToken,
        Long requestId
    ) {
        User currentUser = requireCurrentPatient(userId, identityToken);

        FriendRequest friendRequest =
            friendRequestRepository
                .findById(requestId)
                .orElseThrow(() -> notFound("找不到好友邀請"));

        if (!friendRequest
            .getSender()
            .getId()
            .equals(currentUser.getId())) {
            throw forbidden("你沒有權限取消這個邀請");
        }

        if (!PENDING.equals(friendRequest.getStatus())) {
            throw conflict("只能取消尚未處理的好友邀請");
        }

        friendRequestRepository.delete(friendRequest);

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put(
            "message",
            "好友邀請已取消"
        );

        result.put(
            "requestId",
            requestId
        );

        return result;
    }

    @Transactional
    public Map<String, Object> removeFriend(
        Long userId,
        String identityToken,
        Long friendId
    ) {
        User currentUser = requireCurrentPatient(userId, identityToken);
        if (friendId == null) {
            throw badRequest("缺少 friendId");
        }

        if (currentUser.getId().equals(friendId)) {
            throw badRequest("不能刪除自己");
        }

        User friend = userRepository.findById(friendId)
            .orElseThrow(() -> notFound("找不到好友帳號"));
        if (!hasRole(friend, PATIENT)) {
            throw forbidden("好友功能僅限病患使用");
        }

        long lowId = Math.min(currentUser.getId(), friendId);
        long highId = Math.max(currentUser.getId(), friendId);

        Friendship friendship = friendshipRepository
            .findByUserLowIdAndUserHighId(
                lowId,
                highId
            )
            .orElseThrow(() -> notFound("你們目前不是好友"));

        friendshipRepository.delete(friendship);

        Map<String, Object> result =
            new LinkedHashMap<>();

        result.put(
            "message",
            "好友已刪除"
        );

        result.put(
            "friendId",
            friendId
        );

        return result;
    }

    private User requireCurrentPatient(Long userId, String identityToken) {
        if (userId == null) {
            throw unauthorized("缺少登入身份");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> unauthorized("登入身份無效"));
        if (identityToken == null || identityToken.isBlank()) {
            throw unauthorized("缺少 identity token");
        }
        if (!identityService.isConfigured()) {
            throw new FriendApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Friend identity 尚未設定"
            );
        }
        if (!identityService.isValid(user, identityToken)) {
            throw forbidden("Identity token 無效");
        }
        if (!hasRole(user, PATIENT)) {
            throw forbidden("好友功能僅限病患使用");
        }
        return user;
    }

    private Map<String, Object> toPendingRequest(FriendRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requestId", request.getId());
        item.put("senderId", request.getSender().getId());
        item.put("senderName", safeName(request.getSender()));
        item.put("senderFriendCode", request.getSender().getFriendCode());
        item.put("status", request.getStatus());
        item.put("createdAt", request.getCreatedAt());
        return item;
    }

    private Map<String, Object> toSentRequest(FriendRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requestId", request.getId());
        item.put("receiverId", request.getReceiver().getId());
        item.put("receiverName", safeName(request.getReceiver()));
        item.put("receiverFriendCode", request.getReceiver().getFriendCode());
        item.put("status", request.getStatus());
        item.put("createdAt", request.getCreatedAt());
        return item;
    }

    private boolean hasRole(User user, String role) {
        return user != null
            && user.getRole() != null
            && role.equalsIgnoreCase(user.getRole());
    }

    private FriendApiException badRequest(String message) {
        return new FriendApiException(HttpStatus.BAD_REQUEST, message);
    }

    private FriendApiException unauthorized(String message) {
        return new FriendApiException(HttpStatus.UNAUTHORIZED, message);
    }

    private FriendApiException forbidden(String message) {
        return new FriendApiException(HttpStatus.FORBIDDEN, message);
    }

    private FriendApiException notFound(String message) {
        return new FriendApiException(HttpStatus.NOT_FOUND, message);
    }

    private FriendApiException conflict(String message) {
        return new FriendApiException(HttpStatus.CONFLICT, message);
    }

    private String safeName(User user) {
        if (user.getName() == null ||
            user.getName().isBlank()) {
            return "使用者";
        }

        return user.getName();
    }
}
