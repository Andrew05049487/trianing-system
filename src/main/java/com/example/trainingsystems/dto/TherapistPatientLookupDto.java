package com.example.trainingsystems.dto;

public record TherapistPatientLookupDto(
    Long patientId,
    String patientName,
    String patientEmail
) {}
