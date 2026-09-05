package com.example.trainingsystems.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class CustomRehabExerciseDto {
    private String id;
    private String name;
    private String description;
    private String createdByTherapistId;
    private String createdAt;
    private String updatedAt;
    private Integer repetitions;
    private Integer sets;
    private Double holdSeconds;
    private Double restSeconds;
    private Double duration;
    private JsonNode keyframes;
    private JsonNode evaluationRules;
    private JsonNode poseMeasurementRules;
}
