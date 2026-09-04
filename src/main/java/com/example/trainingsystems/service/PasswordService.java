package com.example.trainingsystems.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

@Service
public class PasswordService {
    private static final Pattern BCRYPT_PATTERN = Pattern.compile(
        "^\\$2[ayb]\\$\\d{2}\\$[./A-Za-z0-9]{53}$"
    );

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String password) {
        return encoder.encode(password);
    }

    public boolean isBcrypt(String storedPassword) {
        return storedPassword != null && BCRYPT_PATTERN.matcher(storedPassword).matches();
    }

    /**
     * Verifies both BCrypt and legacy plaintext values. The caller is responsible
     * for persisting encode(rawPassword) after a successful legacy match.
     */
    public PasswordMatch verify(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return PasswordMatch.NO_MATCH;
        }
        if (isBcrypt(storedPassword)) {
            return encoder.matches(rawPassword, storedPassword)
                ? PasswordMatch.BCRYPT_MATCH
                : PasswordMatch.NO_MATCH;
        }
        boolean matches = MessageDigest.isEqual(
            rawPassword.getBytes(StandardCharsets.UTF_8),
            storedPassword.getBytes(StandardCharsets.UTF_8)
        );
        return matches ? PasswordMatch.LEGACY_MATCH : PasswordMatch.NO_MATCH;
    }

    public enum PasswordMatch {
        NO_MATCH,
        BCRYPT_MATCH,
        LEGACY_MATCH
    }
}
