package com.example.trainingsystems.dto;

import lombok.Data;

@Data
public class CustomExerciseAssignmentDto {
    private Long assignmentId;
    private String exerciseId;
    private String exerciseName;
    private String exerciseDescription;
    private Long therapistId;
    private String therapistName;
    private Long patientId;
    private String patientName;
    private String assignedAt;
}
