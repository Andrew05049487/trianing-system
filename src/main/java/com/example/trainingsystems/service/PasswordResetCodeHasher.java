package com.example.trainingsystems.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class PasswordResetCodeHasher {
    private final byte[] secret;

    public PasswordResetCodeHasher(
        @Value("${PASSWORD_RESET_SECRET:}") String configuredSecret
    ) {
        this.secret = configuredSecret == null
            ? new byte[0]
            : configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String resetId, String code) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(
                (resetId + ":" + code).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", error);
        }
    }

    public boolean matches(String resetId, String code, String expectedHash) {
        byte[] actual = hash(resetId, code).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash == null
            ? new byte[0]
            : expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    public void requireConfigured() {
        if (secret.length < 32) {
            throw new AuthApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PASSWORD_RESET_UNAVAILABLE",
                "密碼重設服務暫時無法使用"
            );
        }
    }
}
