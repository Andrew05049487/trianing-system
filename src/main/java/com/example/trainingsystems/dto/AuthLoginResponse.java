package com.example.trainingsystems.dto;

public record AuthLoginResponse(
    String message,
    Long userId,
    String name,
    String email,
    String role,
    String bindingCode,
    String friendCode,
    String customExerciseToken
) {
}
