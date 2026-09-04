package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.CustomRehabExerciseDto;
import com.example.trainingsystems.service.CustomExerciseAssignmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/custom-exercises")
public class PatientCustomExerciseController {
    private final CustomExerciseAssignmentService service;

    public PatientCustomExerciseController(CustomExerciseAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomRehabExerciseDto> getAll(
        @RequestHeader("X-User-Id") Long patientId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getPatientExercises(patientId, identityToken);
    }

    @GetMapping("/{exerciseId}")
    public CustomRehabExerciseDto getOne(
        @PathVariable String exerciseId,
        @RequestHeader("X-User-Id") Long patientId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getPatientExercise(exerciseId, patientId, identityToken);
    }
}
