package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.TrainingSessionResultDto;
import com.example.trainingsystems.dto.TrainingSessionResultRequest;
import com.example.trainingsystems.service.TrainingSessionResultService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TrainingSessionResultController {
    private final TrainingSessionResultService service;

    public TrainingSessionResultController(TrainingSessionResultService service) {
        this.service = service;
    }

    @PostMapping("/training-results")
    public ResponseEntity<TrainingSessionResultDto> save(
        @RequestHeader("X-User-Id") Long patientId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @RequestBody TrainingSessionResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.save(patientId, identityToken, request));
    }

    @GetMapping("/training-results/me")
    public List<TrainingSessionResultDto> getMine(
        @RequestHeader("X-User-Id") Long patientId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getMine(patientId, identityToken);
    }

    @GetMapping("/therapist/patients/{patientId}/training-results")
    public List<TrainingSessionResultDto> getForTherapist(
        @RequestHeader("X-User-Id") Long therapistId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @PathVariable Long patientId
    ) {
        return service.getForTherapist(therapistId, identityToken, patientId);
    }
}
