package com.example.trainingsystems.dto;

public record ChatContactDto(
    Long userId,
    String name,
    String role,
    String type
) {}
