package com.example.trainingsystems.dto;

public record TherapistRegisterRequest(
    String name,
    String email,
    String password,
    String inviteCode
) {
}
