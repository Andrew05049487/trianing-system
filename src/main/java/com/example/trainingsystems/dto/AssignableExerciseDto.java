package com.example.trainingsystems.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssignableExerciseDto {
    private String id;
    private String name;
    private String description;
    private String type;
    private boolean assigned;
}
