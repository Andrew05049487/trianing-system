package com.example.trainingsystems.controller;

import com.example.trainingsystems.service.ResearchManagementService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ml-research/management")
public class ResearchManagementController {
    private final ResearchManagementService service;

    public ResearchManagementController(ResearchManagementService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public ResearchManagementService.Stats stats(
        @RequestHeader(value = "X-User-Id", required = false) Long id,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token) {
        return service.stats(id, token);
    }

    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<byte[]> export(
        @RequestHeader(value = "X-User-Id", required = false) Long id,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @RequestParam(defaultValue = "standing_knee_raise") String actionId) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=rehab-research-reviewed.zip")
            .body(service.exportApproved(id, token, actionId));
    }
}
