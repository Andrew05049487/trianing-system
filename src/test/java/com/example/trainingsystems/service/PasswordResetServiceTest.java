package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.PasswordResetCredential;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.PasswordResetCredentialRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-06T02:00:00Z");
    private static final String SECRET = "test-password-reset-secret-32-bytes-minimum";

    private UserRepository users;
    private PasswordResetCredentialRepository credentials;
    private PasswordResetEmailService email;
    private PasswordResetCodeGenerator generator;
    private PasswordService passwords;
    private PasswordResetCodeHasher hasher;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        credentials = mock(PasswordResetCredentialRepository.class);
        email = mock(PasswordResetEmailService.class);
        generator = mock(PasswordResetCodeGenerator.class);
        passwords = new PasswordService();
        hasher = new PasswordResetCodeHasher(SECRET);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new PasswordResetService(
            users,
            credentials,
            passwords,
            hasher,
            generator,
            email,
            new AuthAbuseRateLimiter(clock),
            clock
        );
        when(generator.nextCode()).thenReturn("123456");
        when(credentials.findByUserIdAndConsumedAtIsNull(any()))
            .thenReturn(List.of());
    }

    @Test
    void emailRequestStoresOnlyHashAndSendsCode() {
        User user = user(1L, "person@example.com", "old-password");
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        allowLock(user);

        service.requestReset(" Person@Example.COM ");

        ArgumentCaptor<PasswordResetCredential> captor =
            ArgumentCaptor.forClass(PasswordResetCredential.class);
        verify(credentials).saveAndFlush(captor.capture());
        PasswordResetCredential stored = captor.getValue();
        assertEquals(64, stored.getCodeHash().length());
        assertNotEquals("123456", stored.getCodeHash());
        assertTrue(hasher.matches(stored.getId(), "123456", stored.getCodeHash()));
        assertEquals(NOW.plusSeconds(600), stored.getExpiresAt());
        verify(email).sendResetCode(
            "person@example.com",
            "123456",
            PasswordResetService.CODE_LIFETIME
        );
    }

    @Test
    void accountIdRequestUsesLoginNormalization() {
        User user = user(1L, "person@example.com", "old-password");
        when(users.findByAccountId("rehab01")).thenReturn(Optional.of(user));
        allowLock(user);

        service.requestReset(" ReHab01 ");

        verify(users).findByAccountId("rehab01");
        verify(email).sendResetCode(any(), any(), any());
    }

    @Test
    void nonexistentIdentifiersDoNotSendOrPersist() {
        service.requestReset("missing@example.com");

        verify(email, never()).sendResetCode(any(), any(), any());
        verify(credentials, never()).saveAndFlush(any());
    }

    @Test
    void resendConsumesPreviousCredentialBeforeCreatingLatest() {
        User user = user(1L, "person@example.com", "old-password");
        PasswordResetCredential previous = credential(user, "111111", NOW.plusSeconds(300));
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        allowLock(user);
        when(credentials.findByUserIdAndConsumedAtIsNull(1L))
            .thenReturn(List.of(previous));

        service.requestReset("person@example.com");

        assertEquals(NOW, previous.getConsumedAt());
        verify(credentials).saveAllAndFlush(List.of(previous));
    }

    @Test
    void validCodeSetsBcryptPasswordConsumesCodeAndPreservesGoogle() {
        User user = user(1L, "google@example.com", null);
        user.setGoogleSubject("google-subject");
        PasswordResetCredential credential = activeCredential(user, "123456");

        service.resetPassword("google@example.com", "123456", "new-secret");

        assertTrue(passwords.isBcrypt(user.getPassword()));
        assertNotEquals("new-secret", user.getPassword());
        assertNotEquals(PasswordService.PasswordMatch.NO_MATCH,
            passwords.verify("new-secret", user.getPassword()));
        assertEquals("google-subject", user.getGoogleSubject());
        assertEquals(NOW, credential.getConsumedAt());
        verify(users).save(user);
    }

    @Test
    void oldPasswordNoLongerAuthenticatesAfterReset() {
        User user = user(1L, "person@example.com", passwords.encode("old-secret"));
        activeCredential(user, "123456");

        service.resetPassword("person@example.com", "123456", "new-secret");

        assertEquals(PasswordService.PasswordMatch.NO_MATCH,
            passwords.verify("old-secret", user.getPassword()));
        assertNotEquals(PasswordService.PasswordMatch.NO_MATCH,
            passwords.verify("new-secret", user.getPassword()));
    }

    @Test
    void consumedCodeCannotBeReused() {
        User user = user(1L, "person@example.com", "old-password");
        activeCredential(user, "123456");
        service.resetPassword("person@example.com", "123456", "new-secret");
        when(credentials.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(1L))
            .thenReturn(Optional.empty());

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.resetPassword("person@example.com", "123456", "other-secret")
        );
        assertEquals("INVALID_OR_EXPIRED_RESET_CODE", error.getCode());
    }

    @Test
    void expiredCodeIsConsumedAndRejected() {
        User user = user(1L, "person@example.com", "old-password");
        PasswordResetCredential credential =
            credential(user, "123456", NOW.minusSeconds(1));
        arrangeActive(user, credential);

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.resetPassword("person@example.com", "123456", "new-secret")
        );

        assertEquals("INVALID_OR_EXPIRED_RESET_CODE", error.getCode());
        assertEquals(NOW, credential.getConsumedAt());
    }

    @Test
    void incorrectCodeIncrementsPersistedAttemptCount() {
        User user = user(1L, "person@example.com", "old-password");
        PasswordResetCredential credential = activeCredential(user, "123456");

        assertThrows(
            AuthApiException.class,
            () -> service.resetPassword("person@example.com", "654321", "new-secret")
        );

        assertEquals(1, credential.getFailedAttempts());
        assertNull(credential.getConsumedAt());
        verify(credentials).saveAndFlush(credential);
        verify(users, never()).save(any());
    }

    @Test
    void fifthIncorrectCodeInvalidatesCredential() {
        User user = user(1L, "person@example.com", "old-password");
        PasswordResetCredential credential = activeCredential(user, "123456");
        credential.setFailedAttempts(4);

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.resetPassword("person@example.com", "654321", "new-secret")
        );

        assertEquals("TOO_MANY_ATTEMPTS", error.getCode());
        assertEquals(NOW, credential.getConsumedAt());
    }

    @Test
    void codeForUserACannotResetUserB() {
        User userA = user(1L, "a@example.com", "old-a");
        User userB = user(2L, "b@example.com", "old-b");
        PasswordResetCredential credentialA = credential(
            userA,
            "123456",
            NOW.plusSeconds(600)
        );
        when(users.findByEmail("b@example.com")).thenReturn(Optional.of(userB));
        allowLock(userB);
        when(credentials.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(2L))
            .thenReturn(Optional.empty());

        assertThrows(
            AuthApiException.class,
            () -> service.resetPassword("b@example.com", "123456", "new-secret")
        );
        assertNull(credentialA.getConsumedAt());
        verify(users, never()).save(any());
    }

    @Test
    void invalidNewPasswordFailsBeforeAccountLookup() {
        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.resetPassword("person@example.com", "123456", "short")
        );
        assertEquals("INVALID_NEW_PASSWORD", error.getCode());
        verifyNoInteractions(users);
    }

    @Test
    void missingHashSecretFailsClosed() {
        PasswordResetCodeHasher missing = new PasswordResetCodeHasher("");
        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> missing.hash("id", "123456")
        );
        assertEquals("PASSWORD_RESET_UNAVAILABLE", error.getCode());
    }

    private PasswordResetCredential activeCredential(User user, String code) {
        PasswordResetCredential credential =
            credential(user, code, NOW.plusSeconds(600));
        arrangeActive(user, credential);
        return credential;
    }

    private void arrangeActive(User user, PasswordResetCredential credential) {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        allowLock(user);
        when(credentials.findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId()))
            .thenReturn(Optional.of(credential));
        when(credentials.findByUserIdAndConsumedAtIsNull(user.getId()))
            .thenReturn(List.of(credential));
    }

    private void allowLock(User user) {
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    private PasswordResetCredential credential(
        User user,
        String code,
        Instant expiresAt
    ) {
        PasswordResetCredential credential = new PasswordResetCredential();
        credential.setId("reset-id");
        credential.setUser(user);
        credential.setCodeHash(hasher.hash("reset-id", code));
        credential.setCreatedAt(NOW);
        credential.setExpiresAt(expiresAt);
        return credential;
    }

    private User user(Long id, String email, String password) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole("PATIENT");
        return user;
    }
}
