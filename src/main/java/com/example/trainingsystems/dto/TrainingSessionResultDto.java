package com.example.trainingsystems.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainingSessionResultDto {
    private String sessionId;
    private String exerciseType;
    private String exerciseId;
    private String exerciseName;
    private Integer completedSets;
    private Integer completedReps;
    private Integer targetSets;
    private Integer targetReps;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationSeconds;
    private String completionStatus;
    private BigDecimal score;
}
