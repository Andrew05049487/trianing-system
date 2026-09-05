package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.FriendRequestCreateDto;
import com.example.trainingsystems.dto.FriendRequestRespondDto;
import com.example.trainingsystems.entity.FriendRequest;
import com.example.trainingsystems.entity.Friendship;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.FriendRequestRepository;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FriendService {

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;

    public FriendService(
        UserRepository userRepository,
        FriendRequestRepository friendRequestRepository,
        FriendshipRepository friendshipRepository
    ) {
        this.userRepository = userRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional
    public Map<String, Object> sendRequest(
        FriendRequestCreateDto requestDto
    ) {
        if (requestDto.getSenderId() == null) {
            throw new IllegalArgumentException("缺少 senderId");
        }

        if (requestDto.getFriendCode() == null ||
            requestDto.getFriendCode().isBlank()) {
            throw new IllegalArgumentException("請輸入好友代碼");
        }

        User sender = userRepository
            .findById(requestDto.getSenderId())
            .orElseThrow(() ->
                new IllegalArgumentException("找不到發送者帳號")
            );

        String friendCode = requestDto
            .getFriendCode()
            .trim()
            .toUpperCase(Locale.ROOT);

        User receiver = userRepository
            .findByFriendCode(friendCode)
            .orElseThrow(() ->
                new IllegalArgumentException("找不到此好友代碼")
            );

        if (sender.getId().equals(receiver.getId())) {
            throw new IllegalArgumentException("不能加自己為好友");
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
            throw new IllegalArgumentException("你們已經是好友");
        }

        FriendRequest reverseRequest = friendRequestRepository
            .findBySenderIdAndReceiverId(
                receiver.getId(),
                sender.getId()
            )
            .orElse(null);

        if (reverseRequest != null &&
            "PENDING".equals(reverseRequest.getStatus())) {
            throw new IllegalArgumentException(
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
            if ("PENDING".equals(friendRequest.getStatus())) {
                throw new IllegalArgumentException(
                    "好友邀請已經送出"
                );
            }

            friendRequest.setStatus("PENDING");
            friendRequest.setCreatedAt(LocalDateTime.now());
            friendRequest.setRespondedAt(null);
        } else {
            friendRequest = new FriendRequest();
            friendRequest.setSender(sender);
            friendRequest.setReceiver(receiver);
            friendRequest.setStatus("PENDING");
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
        Long receiverId
    ) {
        if (!userRepository.existsById(receiverId)) {
            throw new IllegalArgumentException("找不到使用者");
        }

        return friendRequestRepository
            .findByReceiverIdAndStatusOrderByCreatedAtDesc(
                receiverId,
                "PENDING"
            )
            .stream()
            .map(friendRequest -> {
                Map<String, Object> item =
                    new LinkedHashMap<>();

                item.put(
                    "requestId",
                    friendRequest.getId()
                );

                item.put(
                    "senderId",
                    friendRequest.getSender().getId()
                );

                item.put(
                    "senderName",
                    safeName(friendRequest.getSender())
                );

                item.put(
                    "senderFriendCode",
                    friendRequest
                        .getSender()
                        .getFriendCode()
                );

                item.put(
                    "status",
                    friendRequest.getStatus()
                );

                item.put(
                    "createdAt",
                    friendRequest.getCreatedAt()
                );

                return item;
            })
            .toList();
    }

    @Transactional
    public Map<String, Object> respondToRequest(
        Long requestId,
        FriendRequestRespondDto responseDto
    ) {
        if (responseDto.getReceiverId() == null) {
            throw new IllegalArgumentException(
                "缺少 receiverId"
            );
        }

        if (responseDto.getAction() == null ||
            responseDto.getAction().isBlank()) {
            throw new IllegalArgumentException(
                "缺少 action"
            );
        }

        FriendRequest friendRequest = friendRequestRepository
            .findById(requestId)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "找不到好友邀請"
                )
            );

        if (!friendRequest
            .getReceiver()
            .getId()
            .equals(responseDto.getReceiverId())) {
            throw new IllegalArgumentException(
                "你沒有權限處理這個邀請"
            );
        }

        if (!"PENDING".equals(friendRequest.getStatus())) {
            throw new IllegalArgumentException(
                "這個邀請已處理"
            );
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
            throw new IllegalArgumentException(
                "action 只能是 ACCEPT 或 REJECT"
            );
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
        Long userId
    ) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException(
                "找不到使用者"
            );
        }

        return friendshipRepository
            .findByUserLowIdOrUserHighId(
                userId,
                userId
            )
            .stream()
            .map(friendship -> {
                User friend = friendship
                    .getUserLow()
                    .getId()
                    .equals(userId)
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
        Long requestId,
        Long senderId
    ) {
        if (senderId == null) {
            throw new IllegalArgumentException(
                "缺少 senderId"
            );
        }

        FriendRequest friendRequest =
            friendRequestRepository
                .findById(requestId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "找不到好友邀請"
                    )
                );

        if (!friendRequest
            .getSender()
            .getId()
            .equals(senderId)) {
            throw new IllegalArgumentException(
                "你沒有權限取消這個邀請"
            );
        }

        if (!"PENDING".equals(
            friendRequest.getStatus()
        )) {
            throw new IllegalArgumentException(
                "只能取消尚未處理的好友邀請"
            );
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
        Long friendId
    ) {
        if (userId == null || friendId == null) {
            throw new IllegalArgumentException(
                "缺少 userId 或 friendId"
            );
        }

        if (userId.equals(friendId)) {
            throw new IllegalArgumentException(
                "不能刪除自己"
            );
        }

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException(
                "找不到使用者"
            );
        }

        if (!userRepository.existsById(friendId)) {
            throw new IllegalArgumentException(
                "找不到好友帳號"
            );
        }

        long lowId = Math.min(userId, friendId);
        long highId = Math.max(userId, friendId);

        Friendship friendship = friendshipRepository
            .findByUserLowIdAndUserHighId(
                lowId,
                highId
            )
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "你們目前不是好友"
                )
            );

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

    private String safeName(User user) {
        if (user.getName() == null ||
            user.getName().isBlank()) {
            return "使用者";
        }

        return user.getName();
    }
}