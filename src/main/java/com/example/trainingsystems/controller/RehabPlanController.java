package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.RehabPlanItemDto;
import com.example.trainingsystems.dto.RehabPlanRequest;
import com.example.trainingsystems.dto.RehabPlanResponse;
import com.example.trainingsystems.service.RehabPlanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/plans")
public class RehabPlanController {
    private final RehabPlanService service;

    public RehabPlanController(RehabPlanService service) {
        this.service = service;
    }

    @GetMapping
    public RehabPlanResponse getPlan(
        @RequestParam String patientId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestHeader("X-User-Id") Long requesterId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.getPlan(patientId, date, requesterId, identityToken);
    }

    @PostMapping
    public ResponseEntity<RehabPlanResponse> savePlan(
        @RequestBody RehabPlanRequest request,
        @RequestHeader("X-User-Id") Long requesterId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(service.savePlan(request, requesterId, identityToken));
    }

    @PatchMapping("/{planId}/items/{exerciseId}")
    public RehabPlanResponse updatePlanItem(
        @PathVariable String planId,
        @PathVariable String exerciseId,
        @RequestParam String patientId,
        @RequestBody RehabPlanItemDto request,
        @RequestHeader("X-User-Id") Long requesterId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return service.updatePlanItem(
            patientId,
            planId,
            exerciseId,
            request,
            requesterId,
            identityToken
        );
    }
}
