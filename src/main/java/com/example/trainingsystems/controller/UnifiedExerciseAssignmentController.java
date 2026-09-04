package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AssignableExerciseDto;
import com.example.trainingsystems.service.UnifiedExerciseAssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assignable-exercises")
public class UnifiedExerciseAssignmentController {
    private final UnifiedExerciseAssignmentService service;

    public UnifiedExerciseAssignmentController(
        UnifiedExerciseAssignmentService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<AssignableExerciseDto> getAssignableExercises(
        @RequestParam Long patientId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getAssignableExercises(
            therapistId,
            identityToken,
            patientId
        );
    }

    @PutMapping("/{type}/{exerciseId}/patients/{patientId}")
    public AssignableExerciseDto assign(
        @PathVariable String type,
        @PathVariable String exerciseId,
        @PathVariable Long patientId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.assign(
            type,
            exerciseId,
            patientId,
            therapistId,
            identityToken
        );
    }

    @DeleteMapping("/{type}/{exerciseId}/patients/{patientId}")
    public ResponseEntity<Void> unassign(
        @PathVariable String type,
        @PathVariable String exerciseId,
        @PathVariable Long patientId,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        service.unassign(
            type,
            exerciseId,
            patientId,
            therapistId,
            identityToken
        );
        return ResponseEntity.noContent().build();
    }
}
