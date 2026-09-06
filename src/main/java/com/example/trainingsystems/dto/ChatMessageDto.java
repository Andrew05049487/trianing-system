package com.example.trainingsystems.dto;

import java.time.Instant;

public record ChatMessageDto(
    Long id,
    Long conversationId,
    Long senderId,
    String text,
    Instant sentAt,
    Instant readAt
) {}
