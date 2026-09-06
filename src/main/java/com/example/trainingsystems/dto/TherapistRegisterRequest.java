package com.example.trainingsystems.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TherapistRegisterRequest(
    String name,
    String email,
    String password
) {
}
