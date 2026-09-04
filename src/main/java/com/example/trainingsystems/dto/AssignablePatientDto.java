package com.example.trainingsystems.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignablePatientDto {
    private Long patientId;
    private String patientName;
}
