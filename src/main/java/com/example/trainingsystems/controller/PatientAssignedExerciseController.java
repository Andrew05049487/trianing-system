package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AssignableExerciseDto;
import com.example.trainingsystems.service.UnifiedExerciseAssignmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/patient/assigned-exercises")
public class PatientAssignedExerciseController {
    private final UnifiedExerciseAssignmentService service;

    public PatientAssignedExerciseController(
        UnifiedExerciseAssignmentService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<AssignableExerciseDto> getAll(
        @RequestHeader("X-User-Id") Long patientId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getPatientAssignedExercises(patientId, identityToken);
    }
}
