package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.TherapistRegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TherapistRegistrationServiceTest {
    private static final String INVITE = "test-therapist-invite";
    private UserRepository users;
    private PasswordService passwords;
    private CustomExerciseIdentityService identity;
    private TherapistRegistrationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = new PasswordService();
        identity = mock(CustomExerciseIdentityService.class);
        when(identity.issueToken(any(User.class))).thenReturn("signed-token");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(88L);
            return user;
        });
        service = new TherapistRegistrationService(
            users,
            passwords,
            identity,
            new TherapistInviteVerifier(INVITE),
            new AuthAbuseRateLimiter(
                Clock.fixed(Instant.parse("2026-09-06T02:00:00Z"), ZoneOffset.UTC)
            )
        );
    }

    @Test
    void validInviteCreatesServerControlledTherapistWithBcrypt() {
        when(users.findByEmail("therapist@example.com"))
            .thenReturn(Optional.empty());

        AuthLoginResponse response = service.register(
            request(" Therapist@Example.COM ", "secret1", INVITE),
            "127.0.0.1"
        );

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("THERAPIST", saved.getRole());
        assertEquals("therapist@example.com", saved.getEmail());
        assertTrue(passwords.isBcrypt(saved.getPassword()));
        assertNull(saved.getBindingCode());
        assertNull(saved.getFriendCode());
        assertEquals("THERAPIST", response.role());
        assertEquals("signed-token", response.customExerciseToken());
    }

    @Test
    void registeredTherapistCanUseExistingPasswordLogin() {
        when(users.findByEmail("therapist@example.com"))
            .thenReturn(Optional.empty());
        service.register(
            request("therapist@example.com", "secret1", INVITE),
            "127.0.0.1"
        );
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        when(users.findByEmail("therapist@example.com"))
            .thenReturn(Optional.of(saved));
        AuthService authService = new AuthService(
            users,
            identity,
            passwords,
            mock(GoogleIdentityVerifier.class)
        );
        LoginRequest login = new LoginRequest();
        login.setIdentifier("therapist@example.com");
        login.setPassword("secret1");

        AuthLoginResponse response = authService.login(login);

        assertEquals(88L, response.userId());
        assertEquals("THERAPIST", response.role());
        assertEquals("signed-token", response.customExerciseToken());
    }

    @Test
    void invalidOrMissingInviteReturnsGenericError() {
        for (String invite : new String[]{"wrong-invite", null}) {
            AuthApiException error = assertThrows(
                AuthApiException.class,
                () -> service.register(
                    request("therapist@example.com", "secret1", invite),
                    "source-" + invite
                )
            );
            assertEquals("INVALID_THERAPIST_REGISTRATION", error.getCode());
            assertEquals("治療師註冊資訊無效", error.getMessage());
            assertEquals(400, error.getStatus().value());
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicatePatientEmailCannotBePromoted() {
        User patient = new User();
        patient.setId(1L);
        patient.setRole("PATIENT");
        when(users.findByEmail("patient@example.com"))
            .thenReturn(Optional.of(patient));

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.register(
                request("patient@example.com", "secret1", INVITE),
                "127.0.0.1"
            )
        );

        assertEquals("PATIENT", patient.getRole());
        assertEquals(400, error.getStatus().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void invalidFieldsUseSameGenericError() {
        for (TherapistRegisterRequest request : new TherapistRegisterRequest[]{
            new TherapistRegisterRequest("", "x@example.com", "secret1", INVITE),
            new TherapistRegisterRequest("Name", "", "secret1", INVITE),
            new TherapistRegisterRequest("Name", "x@example.com", "short", INVITE)
        }) {
            AuthApiException error = assertThrows(
                AuthApiException.class,
                () -> service.register(request, request.email())
            );
            assertEquals("INVALID_THERAPIST_REGISTRATION", error.getCode());
        }
    }

    @Test
    void fiveInviteFailuresRateLimitFurtherAttempts() {
        String source = "10.0.0.1";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(
                AuthApiException.class,
                () -> service.register(
                    request("t@example.com", "secret1", "wrong"),
                    source
                )
            );
        }

        AuthApiException blocked = assertThrows(
            AuthApiException.class,
            () -> service.register(
                request("t@example.com", "secret1", INVITE),
                source
            )
        );
        assertEquals(429, blocked.getStatus().value());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void missingServerSecretFailsClosed() {
        TherapistInviteVerifier verifier = new TherapistInviteVerifier("");
        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> verifier.matches("anything")
        );
        assertEquals("THERAPIST_REGISTRATION_UNAVAILABLE", error.getCode());
    }

    private TherapistRegisterRequest request(
        String email,
        String password,
        String invite
    ) {
        return new TherapistRegisterRequest("治療師", email, password, invite);
    }
}
