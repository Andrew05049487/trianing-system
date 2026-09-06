package com.example.trainingsystems.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    private static final String TOKEN = "signed-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBindingRepository userBindingRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private ChatConversationRepository conversationRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private CustomExerciseIdentityService identityService;

    private ChatService service;
    private User patientA;
    private User patientB;
    private User therapist;
    private User outsider;

    @BeforeEach
    void setUp() {
        service = new ChatService(
            userRepository,
            userBindingRepository,
            friendshipRepository,
            conversationRepository,
            messageRepository,
            identityService
        );
        patientA = user(1L, "PATIENT", "病患 A");
        patientB = user(2L, "PATIENT", "病患 B");
        therapist = user(7L, "THERAPIST", "治療師 T");
        outsider = user(99L, "PATIENT", "第三人");
        when(identityService.isConfigured()).thenReturn(true);
    }

    @Test
    void getOrCreateCanonicalizesPairAndReturnsSameConversation() {
        authenticate(patientA);
        authenticate(patientB);
        when(friendshipRepository.existsByUserLowIdAndUserHighId(1L, 2L))
            .thenReturn(true);
        AtomicReference<ChatConversationEntity> stored = new AtomicReference<>();
        when(conversationRepository
            .findByParticipantOne_IdAndParticipantTwo_IdAndConversationType(
                1L,
                2L,
                ChatConversationType.PEER
            )).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(conversationRepository.save(any())).thenAnswer(call -> {
            ChatConversationEntity conversation = call.getArgument(0);
            conversation.setId(123L);
            stored.set(conversation);
            return conversation;
        });

        ChatConversationDto first = service.getOrCreateConversation(
            1L,
            TOKEN,
            2L,
            "peer"
        );
        ChatConversationDto second = service.getOrCreateConversation(
            1L,
            TOKEN,
            2L,
            "peer"
        );
        ChatConversationDto reversed = service.getOrCreateConversation(
            2L,
            TOKEN,
            1L,
            "peer"
        );

        assertThat(first.id()).isEqualTo(123L);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(reversed.id()).isEqualTo(first.id());
        assertThat(first.participantIds()).containsExactly(1L, 2L);
        verify(conversationRepository).save(any(ChatConversationEntity.class));
    }

    @Test
    void therapistBindingAllowsConversation() {
        authenticate(patientA);
        when(userRepository.findById(7L)).thenReturn(Optional.of(therapist));
        when(userBindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                1L,
                7L,
                "THERAPIST"
            )).thenReturn(true);
        when(conversationRepository
            .findByParticipantOne_IdAndParticipantTwo_IdAndConversationType(
                1L,
                7L,
                ChatConversationType.THERAPIST
            )).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(call -> {
            ChatConversationEntity conversation = call.getArgument(0);
            conversation.setId(10L);
            return conversation;
        });

        assertThat(service.getOrCreateConversation(
            1L,
            TOKEN,
            7L,
            "therapist"
        ).id()).isEqualTo(10L);
    }

    @Test
    void therapistConversationWithoutBindingIsForbidden() {
        authenticate(patientA);
        when(userRepository.findById(7L)).thenReturn(Optional.of(therapist));

        assertStatus(
            () -> service.getOrCreateConversation(
                1L,
                TOKEN,
                7L,
                "therapist"
            ),
            HttpStatus.FORBIDDEN
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void acceptedFriendshipAllowsPeerConversation() {
        authenticate(patientA);
        when(userRepository.findById(2L)).thenReturn(Optional.of(patientB));
        when(friendshipRepository.existsByUserLowIdAndUserHighId(1L, 2L))
            .thenReturn(true);
        when(conversationRepository
            .findByParticipantOne_IdAndParticipantTwo_IdAndConversationType(
                1L,
                2L,
                ChatConversationType.PEER
            )).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(call -> {
            ChatConversationEntity conversation = call.getArgument(0);
            conversation.setId(20L);
            return conversation;
        });

        assertThat(service.getOrCreateConversation(
            1L,
            TOKEN,
            2L,
            "peer"
        ).id()).isEqualTo(20L);
    }

    @Test
    void pendingRequestOrNoFriendshipCannotAuthorizePeerConversation() {
        authenticate(patientA);
        when(userRepository.findById(2L)).thenReturn(Optional.of(patientB));
        when(friendshipRepository.existsByUserLowIdAndUserHighId(1L, 2L))
            .thenReturn(false);

        assertStatus(
            () -> service.getOrCreateConversation(1L, TOKEN, 2L, "peer"),
            HttpStatus.FORBIDDEN
        );
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void patientContactsComeFromTherapistBindingAndAcceptedFriendship() {
        authenticate(patientA);
        UserBinding binding = new UserBinding();
        binding.setPatient(patientA);
        binding.setLinkedUser(therapist);
        binding.setRelationship("THERAPIST");
        Friendship friendship = new Friendship();
        friendship.setUserLow(patientA);
        friendship.setUserHigh(patientB);
        when(userBindingRepository.findByPatient_IdOrLinkedUser_Id(1L, 1L))
            .thenReturn(List.of(binding));
        when(friendshipRepository.findByUserLowIdOrUserHighId(1L, 1L))
            .thenReturn(List.of(friendship));

        assertThat(service.getContacts(1L, TOKEN))
            .extracting(contact -> contact.name() + ":" + contact.type())
            .containsExactly("病患 B:peer", "治療師 T:therapist");
    }

    @Test
    void participantCanReadAndSendAndSenderComesFromIdentity() {
        authenticate(patientA);
        ChatConversationEntity conversation = conversation(patientA, patientB);
        when(conversationRepository.findById(50L))
            .thenReturn(Optional.of(conversation));
        ChatMessageEntity older = message(
            1L,
            conversation,
            patientB,
            "older",
            Instant.parse("2026-09-06T01:00:00Z")
        );
        ChatMessageEntity newer = message(
            2L,
            conversation,
            patientA,
            "newer",
            Instant.parse("2026-09-06T02:00:00Z")
        );
        when(messageRepository.findTop200ByConversation_IdOrderBySentAtDesc(50L))
            .thenReturn(List.of(newer, older));
        when(messageRepository.save(any())).thenAnswer(call -> {
            ChatMessageEntity saved = call.getArgument(0);
            saved.setId(3L);
            return saved;
        });

        List<ChatMessageDto> messages = service.getMessages(1L, TOKEN, 50L);
        ChatMessageDto sent = service.sendMessage(
            1L,
            TOKEN,
            50L,
            "  hello  "
        );

        assertThat(messages).extracting(ChatMessageDto::text)
            .containsExactly("older", "newer");
        assertThat(sent.senderId()).isEqualTo(1L);
        assertThat(sent.text()).isEqualTo("hello");
        assertThat(conversation.getLastMessageText()).isEqualTo("hello");
    }

    @Test
    void thirdUserCannotReadSendOrMarkConversation() {
        authenticate(outsider);
        ChatConversationEntity conversation = conversation(patientA, patientB);
        when(conversationRepository.findById(50L))
            .thenReturn(Optional.of(conversation));

        assertStatus(
            () -> service.getMessages(99L, TOKEN, 50L),
            HttpStatus.FORBIDDEN
        );
        assertStatus(
            () -> service.sendMessage(99L, TOKEN, 50L, "guess"),
            HttpStatus.FORBIDDEN
        );
        assertStatus(
            () -> service.markAsRead(99L, TOKEN, 50L),
            HttpStatus.FORBIDDEN
        );
        verify(messageRepository, never()).save(any());
        verify(messageRepository, never()).markUnreadMessagesAsRead(
            any(),
            any(),
            any()
        );
    }

    @Test
    void markReadExcludesMessagesSentByReader() {
        authenticate(patientB);
        ChatConversationEntity conversation = conversation(patientA, patientB);
        when(conversationRepository.findById(50L))
            .thenReturn(Optional.of(conversation));

        service.markAsRead(2L, TOKEN, 50L);

        verify(messageRepository).markUnreadMessagesAsRead(
            org.mockito.ArgumentMatchers.eq(50L),
            org.mockito.ArgumentMatchers.eq(2L),
            any(Instant.class)
        );
    }

    @Test
    void recipientMarkReadSetsReadAtButSenderMarkDoesNot() {
        authenticate(patientA);
        authenticate(patientB);
        ChatConversationEntity conversation = conversation(patientA, patientB);
        ChatMessageEntity message = message(
            1L,
            conversation,
            patientA,
            "hello",
            Instant.parse("2026-09-06T01:00:00Z")
        );
        when(conversationRepository.findById(50L))
            .thenReturn(Optional.of(conversation));
        when(messageRepository.findTop200ByConversation_IdOrderBySentAtDesc(50L))
            .thenReturn(List.of(message));
        when(messageRepository.markUnreadMessagesAsRead(
            any(),
            any(),
            any()
        )).thenAnswer(call -> {
            Long readerId = call.getArgument(1);
            Instant readAt = call.getArgument(2);
            if (!message.getSender().getId().equals(readerId)) {
                message.setReadAt(readAt);
                return 1;
            }
            return 0;
        });

        service.markAsRead(2L, TOKEN, 50L);
        assertThat(service.getMessages(2L, TOKEN, 50L).get(0).readAt())
            .isNotNull();

        message.setReadAt(null);
        service.markAsRead(1L, TOKEN, 50L);
        assertThat(service.getMessages(1L, TOKEN, 50L).get(0).readAt())
            .isNull();
    }

    @Test
    void unreadCountsDropFromTwoToZeroAfterRead() {
        authenticate(patientB);
        ChatConversationEntity conversation = conversation(patientA, patientB);
        when(conversationRepository.findAllForUserOrderByUpdatedAtDesc(2L))
            .thenReturn(List.of(conversation));
        ChatMessageRepository.UnreadCountView count = unreadCount(50L, 2L);
        when(messageRepository.countUnreadByConversationIds(List.of(50L), 2L))
            .thenReturn(List.of(count))
            .thenReturn(List.of());
        when(conversationRepository.findById(50L))
            .thenReturn(Optional.of(conversation));

        List<UnreadCountDto> before = service.getUnreadCounts(2L, TOKEN);
        service.markAsRead(2L, TOKEN, 50L);
        List<UnreadCountDto> after = service.getUnreadCounts(2L, TOKEN);

        assertThat(before).extracting(UnreadCountDto::count).containsExactly(2L);
        assertThat(after).extracting(UnreadCountDto::count).containsExactly(0L);
    }

    @Test
    void invalidIdentityIsForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(patientA));
        when(identityService.isValid(patientA, "wrong")).thenReturn(false);

        assertStatus(
            () -> service.getConversations(1L, "wrong"),
            HttpStatus.FORBIDDEN
        );
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private ChatConversationEntity conversation(User one, User two) {
        ChatConversationEntity conversation = new ChatConversationEntity();
        conversation.setId(50L);
        conversation.setParticipantOne(one);
        conversation.setParticipantTwo(two);
        conversation.setConversationType(ChatConversationType.PEER);
        conversation.setCreatedAt(Instant.parse("2026-09-06T00:00:00Z"));
        conversation.setUpdatedAt(Instant.parse("2026-09-06T00:00:00Z"));
        return conversation;
    }

    private ChatMessageEntity message(
        Long id,
        ChatConversationEntity conversation,
        User sender,
        String text,
        Instant sentAt
    ) {
        ChatMessageEntity message = new ChatMessageEntity();
        message.setId(id);
        message.setConversation(conversation);
        message.setSender(sender);
        message.setText(text);
        message.setSentAt(sentAt);
        return message;
    }

    private ChatMessageRepository.UnreadCountView unreadCount(
        Long conversationId,
        long count
    ) {
        return new ChatMessageRepository.UnreadCountView() {
            @Override
            public Long getConversationId() {
                return conversationId;
            }

            @Override
            public long getUnreadCount() {
                return count;
            }
        };
    }

    private User user(Long id, String role, String name) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName(name);
        user.setEmail(name + "@example.com");
        return user;
    }

    private void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatApiException.class)
            .extracting(error -> ((ChatApiException) error).getStatus())
            .isEqualTo(expected);
    }
}
