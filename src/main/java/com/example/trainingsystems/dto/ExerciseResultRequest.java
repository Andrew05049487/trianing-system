package com.example.trainingsystems.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExerciseResultRequest {

    private Long userId;

    private Long exerciseId;

    private Integer repCount;

    private BigDecimal accuracy;

    private BigDecimal progress;

    private String speedState;

    private Boolean isComplete;
}