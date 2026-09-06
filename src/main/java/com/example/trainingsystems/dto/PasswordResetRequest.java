package com.example.trainingsystems.dto;

public record PasswordResetRequest(
    String identifier,
    String code,
    String newPassword
) {
}
