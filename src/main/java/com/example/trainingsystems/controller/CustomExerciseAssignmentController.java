package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AssignablePatientDto;
import com.example.trainingsystems.dto.CustomExerciseAssignmentDto;
import com.example.trainingsystems.service.CustomExerciseAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/custom-exercise-assignments")
public class CustomExerciseAssignmentController {
    private final CustomExerciseAssignmentService service;

    public CustomExerciseAssignmentController(
        CustomExerciseAssignmentService service
    ) {
        this.service = service;
    }

    @GetMapping("/patients")
    public List<AssignablePatientDto> getAssignablePatients(
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getAssignablePatients(therapistId, identityToken);
    }

    @PutMapping("/{exerciseId}/patients/{patientId}")
    public CustomExerciseAssignmentDto assign(
        @PathVariable String exerciseId,
        @PathVariable Long patientId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.assign(exerciseId, patientId, therapistId, identityToken);
    }

    @DeleteMapping("/{exerciseId}/patients/{patientId}")
    public ResponseEntity<Void> unassign(
        @PathVariable String exerciseId,
        @PathVariable Long patientId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        service.unassign(exerciseId, patientId, therapistId, identityToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exercises/{exerciseId}")
    public List<CustomExerciseAssignmentDto> getExerciseAssignments(
        @PathVariable String exerciseId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getExerciseAssignments(
            exerciseId,
            therapistId,
            identityToken
        );
    }
}
