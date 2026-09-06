package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.PasswordResetCredential;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.PasswordResetCredentialRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {
    public static final String GENERIC_FORGOT_MESSAGE =
        "如果帳號存在，我們已將驗證碼寄送至帳號綁定的 Email。";
    public static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private static final Logger logger =
        LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetCredentialRepository credentialRepository;
    private final PasswordService passwordService;
    private final PasswordResetCodeHasher codeHasher;
    private final PasswordResetCodeGenerator codeGenerator;
    private final PasswordResetEmailService emailService;
    private final AuthAbuseRateLimiter rateLimiter;
    private final Clock clock;

    public PasswordResetService(
        UserRepository userRepository,
        PasswordResetCredentialRepository credentialRepository,
        PasswordService passwordService,
        PasswordResetCodeHasher codeHasher,
        PasswordResetCodeGenerator codeGenerator,
        PasswordResetEmailService emailService,
        AuthAbuseRateLimiter rateLimiter,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordService = passwordService;
        this.codeHasher = codeHasher;
        this.codeGenerator = codeGenerator;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String identifier) {
        codeHasher.requireConfigured();
        String normalized = normalizeIdentifier(identifier);
        String rateLimitKey = normalized == null ? "invalid" : normalized;
        if (!rateLimiter.allowPasswordResetRequest(rateLimitKey)) {
            return;
        }

        String resetId = UUID.randomUUID().toString();
        String code = codeGenerator.nextCode();
        String codeHash = codeHasher.hash(resetId, code);
        Optional<User> user = findUserForUpdate(normalized);
        if (user.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        List<PasswordResetCredential> active =
            credentialRepository.findByUserIdAndConsumedAtIsNull(user.get().getId());
        for (PasswordResetCredential credential : active) {
            credential.setConsumedAt(now);
        }
        credentialRepository.saveAllAndFlush(active);

        PasswordResetCredential credential = new PasswordResetCredential();
        credential.setId(resetId);
        credential.setUser(user.get());
        credential.setCodeHash(codeHash);
        credential.setCreatedAt(now);
        credential.setExpiresAt(now.plus(CODE_LIFETIME));
        credential.setFailedAttempts(0);
        credentialRepository.saveAndFlush(credential);

        sendAfterCommit(user.get().getEmail(), code, user.get().getId());
    }

    @Transactional(noRollbackFor = AuthApiException.class)
    public void resetPassword(
        String identifier,
        String code,
        String newPassword
    ) {
        codeHasher.requireConfigured();
        if (!passwordService.isAcceptableNewPassword(newPassword)) {
            throw new AuthApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_NEW_PASSWORD",
                "新密碼必須為 6～128 個字元"
            );
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw invalidCode();
        }

        String normalized = normalizeIdentifier(identifier);
        User user = findUserForUpdate(normalized).orElseThrow(this::invalidCode);
        PasswordResetCredential credential = credentialRepository
            .findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
            .orElseThrow(this::invalidCode);

        Instant now = clock.instant();
        if (!credential.getExpiresAt().isAfter(now)) {
            credential.setConsumedAt(now);
            credentialRepository.saveAndFlush(credential);
            throw invalidCode();
        }
        if (credential.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            credential.setConsumedAt(now);
            credentialRepository.saveAndFlush(credential);
            throw tooManyAttempts();
        }
        if (!codeHasher.matches(credential.getId(), code, credential.getCodeHash())) {
            int failures = credential.getFailedAttempts() + 1;
            credential.setFailedAttempts(failures);
            if (failures >= MAX_FAILED_ATTEMPTS) {
                credential.setConsumedAt(now);
            }
            credentialRepository.saveAndFlush(credential);
            if (failures >= MAX_FAILED_ATTEMPTS) {
                throw tooManyAttempts();
            }
            throw invalidCode();
        }

        user.setPassword(passwordService.encode(newPassword));
        userRepository.save(user);
        List<PasswordResetCredential> active =
            credentialRepository.findByUserIdAndConsumedAtIsNull(user.getId());
        for (PasswordResetCredential item : active) {
            item.setConsumedAt(now);
        }
        credential.setConsumedAt(now);
        credentialRepository.saveAllAndFlush(active);
        credentialRepository.saveAndFlush(credential);
    }

    private Optional<User> findUser(String normalizedIdentifier) {
        if (normalizedIdentifier == null || normalizedIdentifier.isBlank()) {
            return Optional.empty();
        }
        return normalizedIdentifier.contains("@")
            ? userRepository.findByEmail(normalizedIdentifier)
            : userRepository.findByAccountId(normalizedIdentifier);
    }

    private Optional<User> findUserForUpdate(String normalizedIdentifier) {
        return findUser(normalizedIdentifier).flatMap(
            user -> userRepository.findByIdForUpdate(user.getId())
        );
    }

    private void sendAfterCommit(String email, String code, Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        enqueueResetEmail(email, code, userId);
                    }
                }
            );
            return;
        }
        enqueueResetEmail(email, code, userId);
    }

    private void enqueueResetEmail(String email, String code, Long userId) {
        try {
            emailService.sendResetCode(email, code, CODE_LIFETIME);
        } catch (RuntimeException error) {
            logger.warn("Password reset email could not be queued for userId={}", userId);
        }
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String trimmed = identifier.trim();
        return trimmed.contains("@")
            ? AuthService.normalizeEmail(trimmed)
            : AuthService.normalizeAccountId(trimmed).toLowerCase(Locale.ROOT);
    }

    private AuthApiException invalidCode() {
        return new AuthApiException(
            HttpStatus.BAD_REQUEST,
            "INVALID_OR_EXPIRED_RESET_CODE",
            "驗證碼無效或已過期"
        );
    }

    private AuthApiException tooManyAttempts() {
        return new AuthApiException(
            HttpStatus.TOO_MANY_REQUESTS,
            "TOO_MANY_ATTEMPTS",
            "驗證失敗次數過多，請重新取得驗證碼"
        );
    }
}
