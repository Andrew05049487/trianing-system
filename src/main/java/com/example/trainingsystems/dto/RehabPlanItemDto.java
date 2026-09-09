package com.example.trainingsystems.dto;

public record RehabPlanItemDto(
    String exerciseId,
    Integer order,
    Integer sets,
    Integer repsPerSet,
    Boolean done
) {}
