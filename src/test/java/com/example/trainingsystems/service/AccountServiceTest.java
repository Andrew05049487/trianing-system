package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AccountInfoResponse;
import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.ExerciseResultRepository;
import com.example.trainingsystems.repository.FriendRequestRepository;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private UserRepository users;
    private CustomExerciseIdentityService identity;
    private PasswordService passwords;
    private GoogleIdentityVerifier google;
    private CustomExerciseAssignmentRepository customAssignments;
    private CustomRehabExerciseRepository customExercises;
    private ExerciseAssignmentRepository assignments;
    private ExerciseResultRepository results;
    private UserBindingRepository bindings;
    private FriendRequestRepository friendRequests;
    private FriendshipRepository friendships;
    private AccountService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        identity = mock(CustomExerciseIdentityService.class);
        passwords = new PasswordService();
        google = mock(GoogleIdentityVerifier.class);
        customAssignments = mock(CustomExerciseAssignmentRepository.class);
        customExercises = mock(CustomRehabExerciseRepository.class);
        assignments = mock(ExerciseAssignmentRepository.class);
        results = mock(ExerciseResultRepository.class);
        bindings = mock(UserBindingRepository.class);
        friendRequests = mock(FriendRequestRepository.class);
        friendships = mock(FriendshipRepository.class);
        service = new AccountService(
            users,
            identity,
            passwords,
            google,
            customAssignments,
            customExercises,
            assignments,
            results,
            bindings,
            friendRequests,
            friendships
        );
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(identity.issueToken(any(User.class))).thenReturn("signed-token");
    }

    @Test
    void accountInfoReturnsSafeDerivedIdentityState() {
        User user = patient(null);
        user.setGoogleSubject("google-sub");
        authenticate(user);

        AccountInfoResponse response = service.getAccountInfo(16L, "token");

        assertEquals(16L, response.userId());
        assertEquals("patient@example.com", response.googleEmail());
        assertTrue(response.googleLinked());
        assertFalse(response.hasPassword());
    }

    @Test
    void unauthenticatedAccountOperationIsRejected() {
        User user = patient(null);
        when(users.findById(16L)).thenReturn(Optional.of(user));
        when(identity.isValid(user, "bad-token")).thenReturn(false);

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.getAccountInfo(16L, "bad-token")
        );
        assertEquals("UNAUTHORIZED", error.getCode());
    }

    @Test
    void validAccountIdIsNormalizedAndSavedOnSameUser() {
        User user = patient(passwords.encode("secret1"));
        authenticate(user);
        when(users.findByAccountId("andrew2026")).thenReturn(Optional.empty());

        AccountInfoResponse response = service.updateAccountId(16L, "token", "Andrew2026");

        assertEquals(16L, response.userId());
        assertEquals("andrew2026", user.getAccountId());
        assertEquals("BIND1234", user.getBindingCode());
        verify(users).saveAndFlush(user);
    }

    @Test
    void duplicateAccountIdIsRejectedCaseInsensitively() {
        User user = patient(passwords.encode("secret1"));
        User other = patient(passwords.encode("other11"));
        other.setId(17L);
        authenticate(user);
        when(users.findByAccountId("rehab123")).thenReturn(Optional.of(other));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.updateAccountId(16L, "token", "ReHab123")
        );
        assertEquals("ACCOUNT_ID_ALREADY_IN_USE", error.getCode());
        assertNull(user.getAccountId());
    }

    @Test
    void invalidAccountIdsAreRejected() {
        User user = patient(passwords.encode("secret1"));
        authenticate(user);

        for (String value : List.of("abc", "andrew_123", "安德魯123", "abcdefghijklmnopqrstu")) {
            AuthApiException error = assertThrows(
                AuthApiException.class,
                () -> service.updateAccountId(16L, "token", value)
            );
            assertEquals("INVALID_ACCOUNT_ID", error.getCode());
        }
    }

    @Test
    void googleOnlyPatientCreatesFirstBcryptPasswordAndCanLoginBothWays() {
        User user = patient(null);
        user.setGoogleSubject("google-sub");
        user.setAccountId("rehab123");
        authenticate(user);

        service.updatePassword(16L, "token", null, "secret1");

        assertTrue(passwords.isBcrypt(user.getPassword()));
        assertNotEquals("secret1", user.getPassword());

        AuthService auth = new AuthService(users, identity, passwords, google);
        when(users.findByEmail("patient@example.com")).thenReturn(Optional.of(user));
        when(users.findByAccountId("rehab123")).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        LoginRequest emailLogin = loginRequest("patient@example.com", "secret1");
        LoginRequest idLogin = loginRequest("Rehab123", "secret1");
        assertEquals(16L, auth.login(emailLogin).userId());
        assertEquals(16L, auth.login(idLogin).userId());
    }

    @Test
    void existingPasswordRequiresCorrectCurrentPassword() {
        User user = patient(passwords.encode("secret1"));
        authenticate(user);

        assertThrows(
            AuthApiException.class,
            () -> service.updatePassword(16L, "token", "wrong", "newpass1")
        );
        assertEquals(
            PasswordService.PasswordMatch.BCRYPT_MATCH,
            passwords.verify("secret1", user.getPassword())
        );
    }

    @Test
    void authenticatedPatientLinksGoogleAndUpdatesEmailOnSameUser() {
        User user = patient(passwords.encode("secret1"));
        user.setAccountId("rehab123");
        authenticate(user);
        when(google.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "NEW@gmail.com", "Google User"));
        when(users.findByGoogleSubject("google-sub")).thenReturn(Optional.empty());
        when(users.findByEmail("new@gmail.com")).thenReturn(Optional.empty());

        AuthLoginResponse response = service.linkGoogle(16L, "token", "fresh-token");

        assertEquals(16L, response.userId());
        assertEquals("new@gmail.com", user.getEmail());
        assertEquals("google-sub", user.getGoogleSubject());
        assertEquals("rehab123", user.getAccountId());
        assertEquals("BIND1234", user.getBindingCode());
        verify(users).saveAndFlush(user);
    }

    @Test
    void gmailOwnedByAnotherUserRejectsWithoutMutation() {
        User user = patient(passwords.encode("secret1"));
        User other = patient(passwords.encode("other11"));
        other.setId(17L);
        other.setEmail("used@gmail.com");
        authenticate(user);
        when(google.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "used@gmail.com", "Other"));
        when(users.findByGoogleSubject("google-sub")).thenReturn(Optional.empty());
        when(users.findByEmail("used@gmail.com")).thenReturn(Optional.of(other));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.linkGoogle(16L, "token", "fresh-token")
        );
        assertEquals("GOOGLE_EMAIL_ALREADY_IN_USE", error.getCode());
        assertEquals("patient@example.com", user.getEmail());
        assertNull(user.getGoogleSubject());
    }

    @Test
    void subjectOwnedByAnotherUserIsRejected() {
        User user = patient(passwords.encode("secret1"));
        User other = patient(null);
        other.setId(17L);
        other.setGoogleSubject("google-sub");
        authenticate(user);
        when(google.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "new@gmail.com", "Other"));
        when(users.findByGoogleSubject("google-sub")).thenReturn(Optional.of(other));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.linkGoogle(16L, "token", "fresh-token")
        );
        assertEquals("GOOGLE_ACCOUNT_CONFLICT", error.getCode());
    }

    @Test
    void therapistCannotUseAuthenticatedPatientGoogleLink() {
        User user = patient(passwords.encode("secret1"));
        user.setRole("THERAPIST");
        authenticate(user);
        when(google.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "new@gmail.com", "T"));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.linkGoogle(16L, "token", "fresh-token")
        );
        assertEquals("GOOGLE_PATIENT_ONLY", error.getCode());
    }

    @Test
    void wrongPasswordCannotDeleteAccount() {
        User user = patient(passwords.encode("secret1"));
        authenticate(user);

        assertThrows(
            AuthApiException.class,
            () -> service.deleteAccount(16L, "token", "wrong", null)
        );
        verify(users, never()).delete(any());
    }

    @Test
    void validPasswordDeletesDependentsBeforeUser() {
        User user = patient(passwords.encode("secret1"));
        authenticate(user);
        emptyDependents();

        service.deleteAccount(16L, "token", "secret1", null);

        verify(customAssignments).findAllByPatient_IdOrAssignedByTherapist_Id(16L, 16L);
        verify(assignments).findAllByPatient_IdOrAssignedByTherapist_Id(16L, 16L);
        verify(results).findByUserId(16L);
        verify(bindings).findByPatient_IdOrLinkedUser_Id(16L, 16L);
        verify(friendRequests).findBySenderIdOrReceiverId(16L, 16L);
        verify(friendships).findByUserLowIdOrUserHighId(16L, 16L);
        verify(users).delete(user);
        verify(users).flush();

        when(users.findByEmail("patient@example.com")).thenReturn(Optional.empty());
        AuthService auth = new AuthService(users, identity, passwords, google);
        AuthApiException loginError = assertThrows(
            AuthApiException.class,
            () -> auth.login(loginRequest("patient@example.com", "secret1"))
        );
        assertEquals("INVALID_CREDENTIALS", loginError.getCode());
    }

    @Test
    void googleOnlyDeletionRequiresMatchingFreshSubject() {
        User user = patient(null);
        user.setGoogleSubject("google-sub");
        authenticate(user);
        emptyDependents();
        when(google.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", user.getEmail(), user.getName()));

        service.deleteAccount(16L, "token", null, "fresh-token");

        verify(users).delete(user);
    }

    private void authenticate(User user) {
        when(users.findById(16L)).thenReturn(Optional.of(user));
        when(identity.isValid(user, "token")).thenReturn(true);
    }

    private void emptyDependents() {
        when(customAssignments.findAllByPatient_IdOrAssignedByTherapist_Id(16L, 16L))
            .thenReturn(List.of());
        when(customExercises.findAllByCreatedByTherapist_Id(eq(16L), any(Sort.class)))
            .thenReturn(List.of());
        when(assignments.findAllByPatient_IdOrAssignedByTherapist_Id(16L, 16L))
            .thenReturn(List.of());
        when(results.findByUserId(16L)).thenReturn(List.of());
        when(bindings.findByPatient_IdOrLinkedUser_Id(16L, 16L)).thenReturn(List.of());
        when(friendRequests.findBySenderIdOrReceiverId(16L, 16L)).thenReturn(List.of());
        when(friendships.findByUserLowIdOrUserHighId(16L, 16L)).thenReturn(List.of());
    }

    private User patient(String password) {
        User user = new User();
        user.setId(16L);
        user.setEmail("patient@example.com");
        user.setPassword(password);
        user.setName("測試患者");
        user.setRole("PATIENT");
        user.setBindingCode("BIND1234");
        user.setFriendCode("FRND1234");
        return user;
    }

    private LoginRequest loginRequest(String identifier, String password) {
        LoginRequest request = mock(LoginRequest.class);
        when(request.getIdentifier()).thenReturn(identifier);
        when(request.getPassword()).thenReturn(password);
        return request;
    }
}
