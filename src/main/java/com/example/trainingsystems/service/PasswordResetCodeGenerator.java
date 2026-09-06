package com.example.trainingsystems.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PasswordResetCodeGenerator {
    private final SecureRandom random = new SecureRandom();

    public String nextCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
