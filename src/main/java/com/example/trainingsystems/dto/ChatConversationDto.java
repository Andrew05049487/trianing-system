package com.example.trainingsystems.dto;

import java.time.Instant;
import java.util.List;

public record ChatConversationDto(
    Long id,
    String type,
    List<Long> participantIds,
    String lastMessageText,
    Instant lastMessageAt,
    Instant updatedAt
) {}
