package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.ChatContactDto;
import com.example.trainingsystems.dto.ChatConversationDto;
import com.example.trainingsystems.dto.ChatMessageDto;
import com.example.trainingsystems.dto.UnreadCountDto;
import com.example.trainingsystems.entity.ChatConversationEntity;
import com.example.trainingsystems.entity.ChatConversationType;
import com.example.trainingsystems.entity.ChatMessageEntity;
import com.example.trainingsystems.entity.Friendship;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.ChatConversationRepository;
import com.example.trainingsystems.repository.ChatMessageRepository;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ChatService {
    private static final String PATIENT = "PATIENT";
    private static final String THERAPIST = "THERAPIST";
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final UserRepository userRepository;
    private final UserBindingRepository userBindingRepository;
    private final FriendshipRepository friendshipRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final CustomExerciseIdentityService identityService;

    public ChatService(
        UserRepository userRepository,
        UserBindingRepository userBindingRepository,
        FriendshipRepository friendshipRepository,
        ChatConversationRepository conversationRepository,
        ChatMessageRepository messageRepository,
        CustomExerciseIdentityService identityService
    ) {
        this.userRepository = userRepository;
        this.userBindingRepository = userBindingRepository;
        this.friendshipRepository = friendshipRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.identityService = identityService;
    }

    @Transactional(readOnly = true)
    public List<ChatContactDto> getContacts(Long userId, String identityToken) {
        User currentUser = requireCurrentUser(userId, identityToken);
        Map<String, ChatContactDto> contacts = new LinkedHashMap<>();

        if (hasRole(currentUser, PATIENT)) {
            userBindingRepository
                .findByPatient_IdOrLinkedUser_Id(userId, userId)
                .stream()
                .filter(binding -> isTherapistBindingForPatient(binding, userId))
                .map(UserBinding::getLinkedUser)
                .forEach(user -> addContact(
                    contacts,
                    user,
                    ChatConversationType.THERAPIST
                ));

            friendshipRepository
                .findByUserLowIdOrUserHighId(userId, userId)
                .stream()
                .map(friendship -> otherFriend(friendship, userId))
                .forEach(user -> addContact(
                    contacts,
                    user,
                    ChatConversationType.PEER
                ));
        } else if (hasRole(currentUser, THERAPIST)) {
            userBindingRepository
                .findAllByLinkedUser_IdAndRelationshipIgnoreCase(
                    userId,
                    THERAPIST
                )
                .stream()
                .filter(binding -> hasRole(binding.getPatient(), PATIENT))
                .map(UserBinding::getPatient)
                .forEach(user -> addContact(
                    contacts,
                    user,
                    ChatConversationType.THERAPIST
                ));
        }

        return contacts.values().stream()
            .sorted(Comparator
                .comparing(ChatContactDto::type)
                .thenComparing(
                    ChatContactDto::name,
                    String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(ChatContactDto::userId)
            )
            .toList();
    }

    @Transactional
    public ChatConversationDto getOrCreateConversation(
        Long userId,
        String identityToken,
        Long otherUserId,
        String requestedType
    ) {
        User currentUser = requireCurrentUser(userId, identityToken);
        if (otherUserId == null) {
            throw badRequest("otherUserId 不可為空");
        }
        if (userId.equals(otherUserId)) {
            throw badRequest("不能和自己建立聊天室");
        }
        User otherUser = userRepository.findById(otherUserId)
            .orElseThrow(() -> notFound("聊天對象不存在"));
        ChatConversationType type = parseType(requestedType);
        requireAllowedRelationship(currentUser, otherUser, type);

        User participantOne = userId < otherUserId ? currentUser : otherUser;
        User participantTwo = userId < otherUserId ? otherUser : currentUser;
        return conversationRepository
            .findByParticipantOne_IdAndParticipantTwo_IdAndConversationType(
                participantOne.getId(),
                participantTwo.getId(),
                type
            )
            .map(this::toConversationDto)
            .orElseGet(() -> {
                ChatConversationEntity conversation = new ChatConversationEntity();
                Instant now = Instant.now();
                conversation.setParticipantOne(participantOne);
                conversation.setParticipantTwo(participantTwo);
                conversation.setConversationType(type);
                conversation.setCreatedAt(now);
                conversation.setUpdatedAt(now);
                return toConversationDto(conversationRepository.save(conversation));
            });
    }

    @Transactional(readOnly = true)
    public List<ChatConversationDto> getConversations(
        Long userId,
        String identityToken
    ) {
        requireCurrentUser(userId, identityToken);
        return conversationRepository
            .findAllForUserOrderByUpdatedAtDesc(userId)
            .stream()
            .map(this::toConversationDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(
        Long userId,
        String identityToken,
        Long conversationId
    ) {
        User currentUser = requireCurrentUser(userId, identityToken);
        ChatConversationEntity conversation = requireParticipant(
            conversationId,
            currentUser
        );
        List<ChatMessageEntity> messages = new ArrayList<>(
            messageRepository.findTop200ByConversation_IdOrderBySentAtDesc(
                conversation.getId()
            )
        );
        Collections.reverse(messages);
        return messages.stream().map(this::toMessageDto).toList();
    }

    @Transactional
    public ChatMessageDto sendMessage(
        Long userId,
        String identityToken,
        Long conversationId,
        String rawText
    ) {
        User currentUser = requireCurrentUser(userId, identityToken);
        ChatConversationEntity conversation = requireParticipant(
            conversationId,
            currentUser
        );
        String text = normalizeMessage(rawText);
        Instant now = Instant.now();

        ChatMessageEntity message = new ChatMessageEntity();
        message.setConversation(conversation);
        message.setSender(currentUser);
        message.setText(text);
        message.setSentAt(now);
        ChatMessageEntity savedMessage = messageRepository.save(message);

        conversation.setLastMessageText(text);
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);
        return toMessageDto(savedMessage);
    }

    @Transactional
    public void markAsRead(
        Long userId,
        String identityToken,
        Long conversationId
    ) {
        User currentUser = requireCurrentUser(userId, identityToken);
        ChatConversationEntity conversation = requireParticipant(
            conversationId,
            currentUser
        );
        messageRepository.markUnreadMessagesAsRead(
            conversation.getId(),
            currentUser.getId(),
            Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<UnreadCountDto> getUnreadCounts(
        Long userId,
        String identityToken
    ) {
        requireCurrentUser(userId, identityToken);
        List<Long> conversationIds = conversationRepository
            .findAllForUserOrderByUpdatedAtDesc(userId)
            .stream()
            .map(ChatConversationEntity::getId)
            .toList();
        if (conversationIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> unreadByConversation = new LinkedHashMap<>();
        messageRepository
            .countUnreadByConversationIds(conversationIds, userId)
            .forEach(count -> unreadByConversation.put(
                count.getConversationId(),
                count.getUnreadCount()
            ));
        return conversationIds.stream()
            .map(id -> new UnreadCountDto(
                id,
                unreadByConversation.getOrDefault(id, 0L)
            ))
            .toList();
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
            throw new ChatApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Chat identity 尚未設定"
            );
        }
        if (!identityService.isValid(user, identityToken)) {
            throw forbidden("Identity token 無效");
        }
        return user;
    }

    private ChatConversationEntity requireParticipant(
        Long conversationId,
        User currentUser
    ) {
        if (conversationId == null) {
            throw badRequest("conversationId 不可為空");
        }
        ChatConversationEntity conversation = conversationRepository
            .findById(conversationId)
            .orElseThrow(() -> notFound("聊天室不存在"));
        if (!isParticipant(conversation, currentUser.getId())) {
            throw forbidden("你不是此聊天室的參與者");
        }
        return conversation;
    }

    private void requireAllowedRelationship(
        User currentUser,
        User otherUser,
        ChatConversationType type
    ) {
        long lowId = Math.min(currentUser.getId(), otherUser.getId());
        long highId = Math.max(currentUser.getId(), otherUser.getId());
        if (type == ChatConversationType.PEER) {
            if (!friendshipRepository.existsByUserLowIdAndUserHighId(
                lowId,
                highId
            )) {
                throw forbidden("只有已成為好友的使用者可以建立病友聊天室");
            }
            return;
        }

        Long patientId;
        Long therapistId;
        if (hasRole(currentUser, PATIENT) && hasRole(otherUser, THERAPIST)) {
            patientId = currentUser.getId();
            therapistId = otherUser.getId();
        } else if (
            hasRole(currentUser, THERAPIST) && hasRole(otherUser, PATIENT)
        ) {
            patientId = otherUser.getId();
            therapistId = currentUser.getId();
        } else {
            throw forbidden("只有已綁定的治療師與病患可以建立聊天室");
        }
        if (!userBindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patientId,
                therapistId,
                THERAPIST
            )) {
            throw forbidden("只有已綁定的治療師與病患可以建立聊天室");
        }
    }

    private ChatConversationType parseType(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            throw badRequest("type 不可為空");
        }
        try {
            return ChatConversationType.valueOf(
                requestedType.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException error) {
            throw badRequest("type 只能是 therapist 或 peer");
        }
    }

    private String normalizeMessage(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw badRequest("訊息不可為空白");
        }
        String text = rawText.trim();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw badRequest("訊息不可超過 2000 字元");
        }
        return text;
    }

    private boolean isParticipant(
        ChatConversationEntity conversation,
        Long userId
    ) {
        return conversation.getParticipantOne().getId().equals(userId)
            || conversation.getParticipantTwo().getId().equals(userId);
    }

    private boolean isTherapistBindingForPatient(
        UserBinding binding,
        Long patientId
    ) {
        return binding.getPatient().getId().equals(patientId)
            && THERAPIST.equalsIgnoreCase(binding.getRelationship())
            && hasRole(binding.getLinkedUser(), THERAPIST);
    }

    private User otherFriend(Friendship friendship, Long userId) {
        return friendship.getUserLow().getId().equals(userId)
            ? friendship.getUserHigh()
            : friendship.getUserLow();
    }

    private void addContact(
        Map<String, ChatContactDto> contacts,
        User user,
        ChatConversationType type
    ) {
        String apiType = type.name().toLowerCase(Locale.ROOT);
        contacts.putIfAbsent(
            apiType + ":" + user.getId(),
            new ChatContactDto(
                user.getId(),
                safeName(user),
                user.getRole(),
                apiType
            )
        );
    }

    private boolean hasRole(User user, String role) {
        return user != null
            && user.getRole() != null
            && role.equalsIgnoreCase(user.getRole());
    }

    private String safeName(User user) {
        return user.getName() == null || user.getName().isBlank()
            ? "使用者"
            : user.getName();
    }

    private ChatConversationDto toConversationDto(
        ChatConversationEntity conversation
    ) {
        return new ChatConversationDto(
            conversation.getId(),
            conversation.getConversationType().name().toLowerCase(Locale.ROOT),
            List.of(
                conversation.getParticipantOne().getId(),
                conversation.getParticipantTwo().getId()
            ),
            conversation.getLastMessageText(),
            conversation.getLastMessageAt(),
            conversation.getUpdatedAt()
        );
    }

    private ChatMessageDto toMessageDto(ChatMessageEntity message) {
        return new ChatMessageDto(
            message.getId(),
            message.getConversation().getId(),
            message.getSender().getId(),
            message.getText(),
            message.getSentAt(),
            message.getReadAt()
        );
    }

    private ChatApiException badRequest(String message) {
        return new ChatApiException(HttpStatus.BAD_REQUEST, message);
    }

    private ChatApiException unauthorized(String message) {
        return new ChatApiException(HttpStatus.UNAUTHORIZED, message);
    }

    private ChatApiException forbidden(String message) {
        return new ChatApiException(HttpStatus.FORBIDDEN, message);
    }

    private ChatApiException notFound(String message) {
        return new ChatApiException(HttpStatus.NOT_FOUND, message);
    }
}
