package com.example.trainingsystems.dto;

public record TherapistPatientDto(
    Long patientId,
    String patientName,
    String patientEmail,
    String relationship,
    String boundAt
) {}
