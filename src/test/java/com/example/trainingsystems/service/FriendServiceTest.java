package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.FriendRequestCreateDto;
import com.example.trainingsystems.dto.FriendRequestRespondDto;
import com.example.trainingsystems.entity.FriendRequest;
import com.example.trainingsystems.entity.Friendship;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.FriendRequestRepository;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {
    private static final String TOKEN = "signed-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRequestRepository friendRequestRepository;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private CustomExerciseIdentityService identityService;

    private FriendService service;
    private User patientA;
    private User patientB;
    private User patientC;
    private User therapist;

    @BeforeEach
    void setUp() {
        service = new FriendService(
            userRepository,
            friendRequestRepository,
            friendshipRepository,
            identityService
        );
        patientA = user(1L, "PATIENT", "病患 A", "AAAA1111");
        patientB = user(2L, "PATIENT", "病患 B", "BBBB2222");
        patientC = user(3L, "PATIENT", "病患 C", "CCCC3333");
        therapist = user(9L, "THERAPIST", "治療師", "TTTT9999");
        lenient().when(identityService.isConfigured()).thenReturn(true);
    }

    @Test
    void sendsRequestUsingAuthenticatedPatientAndFriendCode() {
        authenticate(patientA);
        stubNewRequest(patientB);

        Map<String, Object> result = service.sendRequest(
            1L,
            TOKEN,
            createDto("BBBB2222")
        );

        ArgumentCaptor<FriendRequest> captor =
            ArgumentCaptor.forClass(FriendRequest.class);
        verify(friendRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getSender()).isSameAs(patientA);
        assertThat(captor.getValue().getReceiver()).isSameAs(patientB);
        assertThat(result.get("message")).isEqualTo("好友邀請已送出");
    }

    @Test
    void friendCodeLookupIsTrimmedUppercaseAndCaseInsensitive() {
        authenticate(patientA);
        stubNewRequest(patientB);

        service.sendRequest(1L, TOKEN, createDto("  bBbB2222  "));

        verify(userRepository).findByFriendCodeIgnoreCase("BBBB2222");
    }

    @Test
    void invalidFriendCodeReturnsNotFound() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("NOPE0000"))
            .thenReturn(Optional.empty());

        assertError(
            () -> service.sendRequest(1L, TOKEN, createDto("NOPE0000")),
            HttpStatus.NOT_FOUND,
            "找不到此好友代碼"
        );
    }

    @Test
    void cannotAddSelf() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("AAAA1111"))
            .thenReturn(Optional.of(patientA));

        assertError(
            () -> service.sendRequest(1L, TOKEN, createDto("AAAA1111")),
            HttpStatus.BAD_REQUEST,
            "不能加自己為好友"
        );
    }

    @Test
    void therapistCannotUseFriendOperations() {
        authenticate(therapist);

        assertError(
            () -> service.getFriends(9L, TOKEN),
            HttpStatus.FORBIDDEN,
            "好友功能僅限病患使用"
        );
    }

    @Test
    void receiverMustBePatient() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("TTTT9999"))
            .thenReturn(Optional.of(therapist));

        assertError(
            () -> service.sendRequest(1L, TOKEN, createDto("TTTT9999")),
            HttpStatus.FORBIDDEN,
            "只能加入病友為好友"
        );
    }

    @Test
    void existingFriendsCannotBeInvitedAgain() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("BBBB2222"))
            .thenReturn(Optional.of(patientB));
        when(friendshipRepository.existsByUserLowIdAndUserHighId(1L, 2L))
            .thenReturn(true);

        assertError(
            () -> service.sendRequest(1L, TOKEN, createDto("BBBB2222")),
            HttpStatus.CONFLICT,
            "你們已經是好友"
        );
    }

    @Test
    void duplicatePendingRequestIsRejected() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("BBBB2222"))
            .thenReturn(Optional.of(patientB));
        when(friendRequestRepository.findBySenderIdAndReceiverId(2L, 1L))
            .thenReturn(Optional.empty());
        when(friendRequestRepository.findBySenderIdAndReceiverId(1L, 2L))
            .thenReturn(Optional.of(request(11L, patientA, patientB, "PENDING")));

        assertError(
            () -> service.sendRequest(1L, TOKEN, createDto("BBBB2222")),
            HttpStatus.CONFLICT,
            "好友邀請已經送出"
        );
    }

    @Test
    void reversePendingRequestPointsUserToIncomingList() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("BBBB2222"))
            .thenReturn(Optional.of(patientB));
        when(friendRequestRepository.findBySenderIdAndReceiverId(2L, 1L))
            .thenReturn(Optional.of(request(12L, patientB, patientA, "PENDING")));

        assertError(
            () -> service.sendRequest(1L, TOKEN, createDto("BBBB2222")),
            HttpStatus.CONFLICT,
            "對方已向你發送邀請，請到好友邀請中處理"
        );
    }

    @Test
    void receiverCanAcceptRequest() {
        authenticate(patientB);
        FriendRequest request = request(20L, patientA, patientB, "PENDING");
        when(friendRequestRepository.findById(20L)).thenReturn(Optional.of(request));

        Map<String, Object> result = service.respondToRequest(
            2L,
            TOKEN,
            20L,
            respondDto("ACCEPT")
        );

        assertThat(result.get("status")).isEqualTo("ACCEPTED");
        assertThat(request.getRespondedAt()).isNotNull();
    }

    @Test
    void nonReceiverCannotRespond() {
        authenticate(patientC);
        when(friendRequestRepository.findById(20L)).thenReturn(
            Optional.of(request(20L, patientA, patientB, "PENDING"))
        );

        assertError(
            () -> service.respondToRequest(
                3L,
                TOKEN,
                20L,
                respondDto("ACCEPT")
            ),
            HttpStatus.FORBIDDEN,
            "你沒有權限處理這個邀請"
        );
    }

    @Test
    void acceptCreatesFriendshipWithCanonicalOrdering() {
        authenticate(patientB);
        when(friendRequestRepository.findById(20L)).thenReturn(
            Optional.of(request(20L, patientC, patientB, "PENDING"))
        );

        service.respondToRequest(2L, TOKEN, 20L, respondDto("ACCEPT"));

        ArgumentCaptor<Friendship> captor =
            ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(captor.capture());
        assertThat(captor.getValue().getUserLow().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getUserHigh().getId()).isEqualTo(3L);
    }

    @Test
    void acceptDoesNotDuplicateExistingFriendship() {
        authenticate(patientB);
        when(friendRequestRepository.findById(20L)).thenReturn(
            Optional.of(request(20L, patientA, patientB, "PENDING"))
        );
        when(friendshipRepository.existsByUserLowIdAndUserHighId(1L, 2L))
            .thenReturn(true);

        service.respondToRequest(2L, TOKEN, 20L, respondDto("ACCEPT"));

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void rejectDoesNotCreateFriendship() {
        authenticate(patientB);
        FriendRequest request = request(20L, patientA, patientB, "PENDING");
        when(friendRequestRepository.findById(20L)).thenReturn(Optional.of(request));

        service.respondToRequest(2L, TOKEN, 20L, respondDto("REJECT"));

        assertThat(request.getStatus()).isEqualTo("REJECTED");
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void rejectedRequestCanBeSentAgain() {
        authenticate(patientA);
        when(userRepository.findByFriendCodeIgnoreCase("BBBB2222"))
            .thenReturn(Optional.of(patientB));
        when(friendRequestRepository.findBySenderIdAndReceiverId(2L, 1L))
            .thenReturn(Optional.empty());
        FriendRequest rejected = request(20L, patientA, patientB, "REJECTED");
        when(friendRequestRepository.findBySenderIdAndReceiverId(1L, 2L))
            .thenReturn(Optional.of(rejected));
        when(friendRequestRepository.save(rejected)).thenReturn(rejected);

        service.sendRequest(1L, TOKEN, createDto("BBBB2222"));

        assertThat(rejected.getStatus()).isEqualTo("PENDING");
        assertThat(rejected.getRespondedAt()).isNull();
    }

    @Test
    void senderCanCancelPendingRequest() {
        authenticate(patientA);
        FriendRequest request = request(30L, patientA, patientB, "PENDING");
        when(friendRequestRepository.findById(30L)).thenReturn(Optional.of(request));

        service.cancelRequest(1L, TOKEN, 30L);

        verify(friendRequestRepository).delete(request);
    }

    @Test
    void nonSenderCannotCancelRequest() {
        authenticate(patientC);
        when(friendRequestRepository.findById(30L)).thenReturn(
            Optional.of(request(30L, patientA, patientB, "PENDING"))
        );

        assertError(
            () -> service.cancelRequest(3L, TOKEN, 30L),
            HttpStatus.FORBIDDEN,
            "你沒有權限取消這個邀請"
        );
    }

    @Test
    void pendingListIsScopedToAuthenticatedReceiver() {
        authenticate(patientB);
        FriendRequest request = request(40L, patientA, patientB, "PENDING");
        when(friendRequestRepository
            .findByReceiverIdAndStatusOrderByCreatedAtDesc(2L, "PENDING"))
            .thenReturn(List.of(request));

        List<Map<String, Object>> result =
            service.getPendingRequests(2L, TOKEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("senderId")).isEqualTo(1L);
    }

    @Test
    void sentListIsScopedToAuthenticatedSender() {
        authenticate(patientA);
        FriendRequest request = request(41L, patientA, patientB, "PENDING");
        when(friendRequestRepository
            .findBySenderIdAndStatusOrderByCreatedAtDesc(1L, "PENDING"))
            .thenReturn(List.of(request));

        List<Map<String, Object>> result = service.getSentRequests(1L, TOKEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("receiverId")).isEqualTo(2L);
        assertThat(result.get(0).get("receiverFriendCode"))
            .isEqualTo("BBBB2222");
    }

    @Test
    void friendListIsScopedToAuthenticatedPatient() {
        authenticate(patientA);
        Friendship friendship = friendship(50L, patientA, patientB);
        when(friendshipRepository.findByUserLowIdOrUserHighId(1L, 1L))
            .thenReturn(List.of(friendship));

        List<Map<String, Object>> result = service.getFriends(1L, TOKEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("friendId")).isEqualTo(2L);
    }

    @Test
    void authenticatedPatientCanRemoveFriend() {
        authenticate(patientA);
        when(userRepository.findById(2L)).thenReturn(Optional.of(patientB));
        Friendship friendship = friendship(50L, patientA, patientB);
        when(friendshipRepository.findByUserLowIdAndUserHighId(1L, 2L))
            .thenReturn(Optional.of(friendship));

        service.removeFriend(1L, TOKEN, 2L);

        verify(friendshipRepository).delete(friendship);
    }

    @Test
    void authenticatedUserCannotImpersonateRequestOwner() {
        authenticate(patientC);
        when(friendRequestRepository.findById(30L)).thenReturn(
            Optional.of(request(30L, patientA, patientB, "PENDING"))
        );

        assertError(
            () -> service.cancelRequest(3L, TOKEN, 30L),
            HttpStatus.FORBIDDEN,
            "你沒有權限取消這個邀請"
        );
        verify(friendRequestRepository, never()).delete(any());
    }

    @Test
    void missingIdentityIsUnauthorized() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(patientA));

        assertError(
            () -> service.getFriends(1L, null),
            HttpStatus.UNAUTHORIZED,
            "缺少 identity token"
        );
    }

    @Test
    void missingUserIdIsUnauthorized() {
        assertError(
            () -> service.getFriends(null, TOKEN),
            HttpStatus.UNAUTHORIZED,
            "缺少登入身份"
        );
    }

    @Test
    void unknownUserIsUnauthorized() {
        when(userRepository.findById(88L)).thenReturn(Optional.empty());

        assertError(
            () -> service.getFriends(88L, TOKEN),
            HttpStatus.UNAUTHORIZED,
            "登入身份無效"
        );
    }

    @Test
    void invalidIdentityTokenIsForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(patientA));
        when(identityService.isValid(patientA, "wrong")).thenReturn(false);

        assertError(
            () -> service.getFriends(1L, "wrong"),
            HttpStatus.FORBIDDEN,
            "Identity token 無效"
        );
    }

    @Test
    void unconfiguredIdentityServiceIsUnavailable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(patientA));
        when(identityService.isConfigured()).thenReturn(false);

        assertError(
            () -> service.getFriends(1L, TOKEN),
            HttpStatus.SERVICE_UNAVAILABLE,
            "Friend identity 尚未設定"
        );
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private void stubNewRequest(User receiver) {
        when(userRepository.findByFriendCodeIgnoreCase(receiver.getFriendCode()))
            .thenReturn(Optional.of(receiver));
        when(friendRequestRepository.findBySenderIdAndReceiverId(
            receiver.getId(),
            patientA.getId()
        )).thenReturn(Optional.empty());
        when(friendRequestRepository.findBySenderIdAndReceiverId(
            patientA.getId(),
            receiver.getId()
        )).thenReturn(Optional.empty());
        when(friendRequestRepository.save(any())).thenAnswer(call -> {
            FriendRequest saved = call.getArgument(0);
            saved.setId(10L);
            return saved;
        });
    }

    private FriendRequestCreateDto createDto(String friendCode) {
        FriendRequestCreateDto dto = new FriendRequestCreateDto();
        dto.setFriendCode(friendCode);
        return dto;
    }

    private FriendRequestRespondDto respondDto(String action) {
        FriendRequestRespondDto dto = new FriendRequestRespondDto();
        dto.setAction(action);
        return dto;
    }

    private FriendRequest request(
        Long id,
        User sender,
        User receiver,
        String status
    ) {
        FriendRequest request = new FriendRequest();
        request.setId(id);
        request.setSender(sender);
        request.setReceiver(receiver);
        request.setStatus(status);
        request.setCreatedAt(LocalDateTime.of(2026, 9, 6, 10, 0));
        return request;
    }

    private Friendship friendship(Long id, User low, User high) {
        Friendship friendship = new Friendship();
        friendship.setId(id);
        friendship.setUserLow(low);
        friendship.setUserHigh(high);
        friendship.setCreatedAt(LocalDateTime.of(2026, 9, 6, 10, 0));
        return friendship;
    }

    private User user(
        Long id,
        String role,
        String name,
        String friendCode
    ) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName(name);
        user.setFriendCode(friendCode);
        user.setEmail(name + "@example.com");
        return user;
    }

    private void assertError(
        Runnable action,
        HttpStatus status,
        String message
    ) {
        assertThatThrownBy(action::run)
            .isInstanceOf(FriendApiException.class)
            .satisfies(error -> {
                FriendApiException apiError = (FriendApiException) error;
                assertThat(apiError.getStatus()).isEqualTo(status);
                assertThat(apiError.getMessage()).isEqualTo(message);
            });
    }
}
