package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class CustomExerciseIdentityService {
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public CustomExerciseIdentityService(
        @Value("${custom.exercise.identity-secret:}") String identitySecret
    ) {
        this.secret = identitySecret.getBytes(StandardCharsets.UTF_8);
    }

    public String issueToken(User user) {
        if (!isConfigured() || user == null || user.getId() == null) {
            return null;
        }
        return sign(payload(user));
    }

    public boolean isValid(User user, String token) {
        if (!isConfigured() || token == null || token.isBlank()) {
            return false;
        }
        byte[] expected = sign(payload(user)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = token.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    public boolean isConfigured() {
        return secret.length >= 32;
    }

    private String payload(User user) {
        return user.getId() + ":" + String.valueOf(user.getRole());
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("無法建立 Custom Exercise identity token", error);
        }
    }
}
