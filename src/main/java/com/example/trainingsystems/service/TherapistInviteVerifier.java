package com.example.trainingsystems.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class TherapistInviteVerifier {
    private final byte[] configuredSecret;

    public TherapistInviteVerifier(
        @Value("${THERAPIST_REGISTRATION_SECRET:}") String secret
    ) {
        this.configuredSecret = secret == null
            ? new byte[0]
            : secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean matches(String candidate) {
        requireConfigured();
        byte[] supplied = candidate == null
            ? new byte[0]
            : candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredSecret, supplied);
    }

    private void requireConfigured() {
        if (configuredSecret.length < 16) {
            throw new AuthApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "THERAPIST_REGISTRATION_UNAVAILABLE",
                "治療師註冊服務暫時無法使用"
            );
        }
    }
}
