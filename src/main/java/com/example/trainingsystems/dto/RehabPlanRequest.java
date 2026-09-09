package com.example.trainingsystems.dto;

import java.util.List;

public record RehabPlanRequest(
    String patientId,
    String planId,
    String createdBy,
    String date,
    String condition,
    List<RehabPlanItemDto> items
) {}
