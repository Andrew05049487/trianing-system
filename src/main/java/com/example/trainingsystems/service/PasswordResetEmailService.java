package com.example.trainingsystems.service;

import java.time.Duration;

public interface PasswordResetEmailService {
    void sendResetCode(String email, String code, Duration validFor);
}
