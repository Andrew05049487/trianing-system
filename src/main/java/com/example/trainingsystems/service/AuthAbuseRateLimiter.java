package com.example.trainingsystems.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthAbuseRateLimiter {
    private static final Duration RESET_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration RESET_WINDOW = Duration.ofHours(1);
    private static final int RESET_LIMIT = 5;
    private static final Duration INVITE_WINDOW = Duration.ofMinutes(15);
    private static final int INVITE_FAILURE_LIMIT = 5;

    private final Clock clock;
    private final Map<String, Deque<Instant>> resetRequests =
        new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> inviteFailures =
        new ConcurrentHashMap<>();

    public AuthAbuseRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean allowPasswordResetRequest(String identifier) {
        String key = digest("reset:" + identifier);
        Instant now = clock.instant();
        Deque<Instant> attempts = resetRequests.computeIfAbsent(
            key,
            ignored -> new ArrayDeque<>()
        );
        prune(attempts, now.minus(RESET_WINDOW));
        if (!attempts.isEmpty() &&
            Duration.between(attempts.getLast(), now).compareTo(RESET_COOLDOWN) < 0) {
            return false;
        }
        if (attempts.size() >= RESET_LIMIT) {
            return false;
        }
        attempts.addLast(now);
        return true;
    }

    public synchronized boolean isTherapistRegistrationBlocked(String sourceKey) {
        String key = digest("therapist:" + sourceKey);
        Deque<Instant> attempts = inviteFailures.get(key);
        if (attempts == null) {
            return false;
        }
        prune(attempts, clock.instant().minus(INVITE_WINDOW));
        return attempts.size() >= INVITE_FAILURE_LIMIT;
    }

    public synchronized void recordTherapistInviteFailure(String sourceKey) {
        String key = digest("therapist:" + sourceKey);
        Instant now = clock.instant();
        Deque<Instant> attempts = inviteFailures.computeIfAbsent(
            key,
            ignored -> new ArrayDeque<>()
        );
        prune(attempts, now.minus(INVITE_WINDOW));
        attempts.addLast(now);
    }

    public synchronized void clearTherapistInviteFailures(String sourceKey) {
        inviteFailures.remove(digest("therapist:" + sourceKey));
    }

    private void prune(Deque<Instant> attempts, Instant cutoff) {
        while (!attempts.isEmpty() && attempts.getFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
