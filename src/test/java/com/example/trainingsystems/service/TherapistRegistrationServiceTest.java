package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.TherapistRegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            new AuthAbuseRateLimiter(
                Clock.fixed(Instant.parse("2026-09-06T02:00:00Z"), ZoneOffset.UTC)
            )
        );
    }

    @Test
    void directRegistrationCreatesServerControlledTherapistWithBcrypt() {
        when(users.findByEmail("therapist@example.com"))
            .thenReturn(Optional.empty());

        AuthLoginResponse response = service.register(
            request(" Therapist@Example.COM ", "secret1"),
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
            request("therapist@example.com", "secret1"),
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
    void missingLegacyRegistrationSecretDoesNotDisableRegistration() {
        when(users.findByEmail("therapist@example.com"))
            .thenReturn(Optional.empty());

        AuthLoginResponse response = service.register(
            request("therapist@example.com", "secret1"),
            "127.0.0.1"
        );

        assertEquals("THERAPIST", response.role());
        verify(users).saveAndFlush(any(User.class));
    }

    @Test
    void clientRoleAndLegacyInviteFieldsCannotChangeServerControlledRole()
        throws Exception {
        TherapistRegisterRequest request = new ObjectMapper().readValue(
            """
            {
              "name": "治療師",
              "email": "therapist@example.com",
              "password": "secret1",
              "role": "ADMIN",
              "inviteCode": "legacy-value"
            }
            """,
            TherapistRegisterRequest.class
        );
        when(users.findByEmail("therapist@example.com"))
            .thenReturn(Optional.empty());

        service.register(request, "127.0.0.2");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        assertEquals("THERAPIST", captor.getValue().getRole());
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
                request("patient@example.com", "secret1"),
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
            new TherapistRegisterRequest("", "x@example.com", "secret1"),
            new TherapistRegisterRequest("Name", "", "secret1"),
            new TherapistRegisterRequest("Name", "x@example.com", "short")
        }) {
            AuthApiException error = assertThrows(
                AuthApiException.class,
                () -> service.register(request, request.email())
            );
            assertEquals("INVALID_THERAPIST_REGISTRATION", error.getCode());
        }
    }

    @Test
    void fiveRegistrationAttemptsRateLimitFurtherAttempts() {
        String source = "10.0.0.1";
        for (int attempt = 0; attempt < 5; attempt++) {
            String email = "t" + attempt + "@example.com";
            when(users.findByEmail(email)).thenReturn(Optional.empty());
            service.register(
                request(email, "secret1"),
                source
            );
        }

        AuthApiException blocked = assertThrows(
            AuthApiException.class,
            () -> service.register(
                request("another@example.com", "secret1"),
                source
            )
        );
        assertEquals(429, blocked.getStatus().value());
        verify(users, times(5)).saveAndFlush(any(User.class));
    }

    private TherapistRegisterRequest request(
        String email,
        String password
    ) {
        return new TherapistRegisterRequest("治療師", email, password);
    }
}
