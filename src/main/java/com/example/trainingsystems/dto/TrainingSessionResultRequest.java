package com.example.trainingsystems.dto;

import lombok.Data;

@Data
public class TrainingSessionResultRequest {
    private String sessionId;
    private String exerciseType;
    private String exerciseId;
    private Integer completedSets;
    private Integer completedReps;
    private Integer targetSets;
    private Integer targetReps;
    private Long durationSeconds;
    private String completionStatus;
}
