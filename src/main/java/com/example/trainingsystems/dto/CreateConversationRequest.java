package com.example.trainingsystems.dto;

public record CreateConversationRequest(
    Long otherUserId,
    String type
) {}
