package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.CustomRehabExerciseDto;
import com.example.trainingsystems.service.CustomRehabExerciseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/custom-exercises")
public class CustomRehabExerciseController {
    private final CustomRehabExerciseService service;

    public CustomRehabExerciseController(CustomRehabExerciseService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomRehabExerciseDto> getAll(
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getAll(therapistId, identityToken);
    }

    @GetMapping("/{id}")
    public CustomRehabExerciseDto getOne(
        @PathVariable String id,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.get(id, therapistId, identityToken);
    }

    @PutMapping("/{id}")
    public CustomRehabExerciseDto save(
        @PathVariable String id,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @RequestBody CustomRehabExerciseDto request
    ) {
        return service.save(id, therapistId, identityToken, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable String id,
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        service.delete(id, therapistId, identityToken);
        return ResponseEntity.noContent().build();
    }
}
