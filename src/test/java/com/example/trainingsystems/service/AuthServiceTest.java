package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserRepository userRepository;
    private CustomExerciseIdentityService identityService;
    private GoogleIdentityVerifier googleVerifier;
    private PasswordService passwordService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        identityService = mock(CustomExerciseIdentityService.class);
        googleVerifier = mock(GoogleIdentityVerifier.class);
        passwordService = new PasswordService();
        authService = new AuthService(
            userRepository,
            identityService,
            passwordService,
            googleVerifier
        );

        when(userRepository.findByBindingCode(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByFriendCode(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByGoogleSubject(anyString())).thenReturn(Optional.empty());
        when(identityService.issueToken(any(User.class))).thenReturn("signed-token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(99L);
            }
            return user;
        });
    }

    @Test
    void newRegistrationStoresBcryptAndPreservesPatientIdentityFields() {
        RegisterRequest request = registerRequest(" Person@Example.COM ", "secret1", " 小明 ");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.empty());

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertTrue(passwordService.isBcrypt(saved.getPassword()));
        assertNotEquals("secret1", saved.getPassword());
        assertTrue(passwordService.verify("secret1", saved.getPassword()) !=
            PasswordService.PasswordMatch.NO_MATCH);
        assertEquals("person@example.com", saved.getEmail());
        assertEquals("PATIENT", saved.getRole());
        assertNotNull(saved.getBindingCode());
        assertNotNull(saved.getFriendCode());
    }

    @Test
    void bcryptLoginSucceedsAndReturnsExistingContract() {
        User user = patient(7L, "person@example.com", passwordService.encode("secret1"));
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        AuthLoginResponse response = authService.login(loginRequest("PERSON@example.com", "secret1"));

        assertEquals(7L, response.userId());
        assertEquals("PATIENT", response.role());
        assertEquals("signed-token", response.customExerciseToken());
        assertEquals("BIND1234", response.bindingCode());
        assertEquals("FRND1234", response.friendCode());
    }

    @Test
    void accountIdLoginIsCaseInsensitiveAndPreservesRole() {
        User user = patient(7L, "person@example.com", passwordService.encode("secret1"));
        user.setAccountId("rehab123");
        when(userRepository.findByAccountId("rehab123")).thenReturn(Optional.of(user));

        AuthLoginResponse response = authService.login(loginRequest("ReHab123", "secret1"));

        assertEquals(7L, response.userId());
        assertEquals("rehab123", response.accountId());
        assertEquals("PATIENT", response.role());
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void wrongPasswordFails() {
        User user = patient(7L, "person@example.com", passwordService.encode("secret1"));
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> authService.login(loginRequest("person@example.com", "wrong"))
        );
        assertEquals("INVALID_CREDENTIALS", error.getCode());
    }

    @Test
    void legacyPlaintextLoginSucceedsAndUpgradesPassword() {
        User user = patient(7L, "legacy@example.com", "legacy-secret");
        when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.of(user));

        authService.login(loginRequest("legacy@example.com", "legacy-secret"));

        assertTrue(passwordService.isBcrypt(user.getPassword()));
        assertNotEquals("legacy-secret", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void nullPasswordAccountFailsSafely() {
        User user = patient(7L, "google@example.com", null);
        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(user));

        assertThrows(
            AuthApiException.class,
            () -> authService.login(loginRequest("google@example.com", "anything"))
        );
    }

    @Test
    void newGoogleIdentityCreatesPatientWithCodesAndNoPassword() {
        when(googleVerifier.verify("token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "NEW@Example.com", "Google 小明"));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        AuthLoginResponse response = authService.googleLogin("token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("google-sub", saved.getGoogleSubject());
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("PATIENT", saved.getRole());
        assertNull(saved.getPassword());
        assertNotNull(saved.getBindingCode());
        assertNotNull(saved.getFriendCode());
        assertEquals(99L, response.userId());
        assertEquals("signed-token", response.customExerciseToken());
    }

    @Test
    void existingGoogleSubjectLogsIntoSameUser() {
        User user = patient(8L, "linked@example.com", null);
        user.setGoogleSubject("google-sub");
        when(googleVerifier.verify("token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "linked@example.com", "Linked"));
        when(userRepository.findByGoogleSubject("google-sub")).thenReturn(Optional.of(user));

        assertEquals(8L, authService.googleLogin("token").userId());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonPatientGoogleSubjectIsRejected() {
        User therapist = patient(8L, "therapist@example.com", null);
        therapist.setRole("THERAPIST");
        therapist.setGoogleSubject("google-sub");
        when(googleVerifier.verify("token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "therapist@example.com", "T"));
        when(userRepository.findByGoogleSubject("google-sub")).thenReturn(Optional.of(therapist));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> authService.googleLogin("token")
        );
        assertEquals("GOOGLE_PATIENT_ONLY", error.getCode());
    }

    @Test
    void matchingPatientEmailRequiresPasswordLinkAndDoesNotMutate() {
        User user = patient(4L, "existing@example.com", passwordService.encode("secret1"));
        when(googleVerifier.verify("token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "existing@example.com", "E"));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> authService.googleLogin("token")
        );
        assertEquals("GOOGLE_LINK_REQUIRED", error.getCode());
        assertNull(user.getGoogleSubject());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void correctPasswordAndVerifiedGoogleTokenLinksAccount() {
        User user = patient(4L, "existing@example.com", passwordService.encode("secret1"));
        when(googleVerifier.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "existing@example.com", "E"));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));

        AuthLoginResponse response = authService.linkGoogle("fresh-token", "secret1");

        assertEquals("google-sub", user.getGoogleSubject());
        assertEquals(4L, response.userId());
        assertEquals("signed-token", response.customExerciseToken());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void wrongPasswordDoesNotLink() {
        User user = patient(4L, "existing@example.com", passwordService.encode("secret1"));
        when(googleVerifier.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "existing@example.com", "E"));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));

        assertThrows(
            AuthApiException.class,
            () -> authService.linkGoogle("fresh-token", "wrong")
        );
        assertNull(user.getGoogleSubject());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void subjectAlreadyUsedByAnotherUserIsRejected() {
        User user = patient(4L, "existing@example.com", passwordService.encode("secret1"));
        User other = patient(5L, "other@example.com", null);
        other.setGoogleSubject("google-sub");
        when(googleVerifier.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "existing@example.com", "E"));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByGoogleSubject("google-sub")).thenReturn(Optional.of(other));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> authService.linkGoogle("fresh-token", "secret1")
        );
        assertEquals("GOOGLE_ACCOUNT_CONFLICT", error.getCode());
        assertNull(user.getGoogleSubject());
    }

    @Test
    void differentExistingSubjectIsNeverOverwritten() {
        User user = patient(4L, "existing@example.com", passwordService.encode("secret1"));
        user.setGoogleSubject("old-sub");
        when(googleVerifier.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("new-sub", "existing@example.com", "E"));
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(user));

        assertThrows(
            AuthApiException.class,
            () -> authService.linkGoogle("fresh-token", "secret1")
        );
        assertEquals("old-sub", user.getGoogleSubject());
    }

    @Test
    void therapistEmailCollisionIsRejectedWithoutDuplicatePatient() {
        User therapist = patient(3L, "therapist@example.com", passwordService.encode("secret1"));
        therapist.setRole("THERAPIST");
        when(googleVerifier.verify("token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "therapist@example.com", "T"));
        when(userRepository.findByEmail("therapist@example.com")).thenReturn(Optional.of(therapist));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> authService.googleLogin("token")
        );
        assertEquals("GOOGLE_PATIENT_ONLY", error.getCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentGoogleRegistrationConstraintConflictIsSafe() {
        when(googleVerifier.verify("token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "new@example.com", "New"));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("unique constraint"));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> authService.googleLogin("token")
        );
        assertEquals("GOOGLE_ACCOUNT_CONFLICT", error.getCode());
        assertFalse(error.getMessage().contains("constraint"));
    }

    @Test
    void legacyPasswordIsUpgradedDuringGoogleLink() {
        User user = patient(4L, "legacy@example.com", "legacy-secret");
        when(googleVerifier.verify("fresh-token"))
            .thenReturn(new VerifiedGoogleIdentity("google-sub", "legacy@example.com", "Legacy"));
        when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.of(user));

        authService.linkGoogle("fresh-token", "legacy-secret");

        assertEquals("google-sub", user.getGoogleSubject());
        assertTrue(passwordService.isBcrypt(user.getPassword()));
        assertNotEquals("legacy-secret", user.getPassword());
    }

    private User patient(Long id, String email, String password) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword(password);
        user.setName("測試患者");
        user.setRole("PATIENT");
        user.setBindingCode("BIND1234");
        user.setFriendCode("FRND1234");
        return user;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = mock(LoginRequest.class);
        when(request.getIdentifier()).thenReturn(email);
        when(request.getPassword()).thenReturn(password);
        return request;
    }

    private RegisterRequest registerRequest(String email, String password, String name) {
        RegisterRequest request = mock(RegisterRequest.class);
        when(request.getEmail()).thenReturn(email);
        when(request.getPassword()).thenReturn(password);
        when(request.getName()).thenReturn(name);
        return request;
    }
}
