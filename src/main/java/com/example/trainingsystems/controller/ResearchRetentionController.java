package com.example.trainingsystems.controller;

import com.example.trainingsystems.service.ResearchRetentionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/ml-research/management/retention")
public class ResearchRetentionController {
    private final ResearchRetentionService service;

    public ResearchRetentionController(ResearchRetentionService service) { this.service = service; }

    @GetMapping
    public List<ResearchRetentionService.PolicyView> history(
        @RequestHeader(value = "X-User-Id", required = false) Long id,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token) {
        return service.history(id, token);
    }

    @PostMapping
    public ResearchRetentionService.PolicyView create(
        @RequestHeader(value = "X-User-Id", required = false) Long id,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @RequestBody PolicyRequest request) {
        return service.createPolicy(id, token, request.policyVersion(), request.retentionDays(),
            request.effectiveAt(), request.approvalReference());
    }

    @PostMapping("/process-expired")
    public ProcessResult processExpired(
        @RequestHeader(value = "X-User-Id", required = false) Long id,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token) {
        return new ProcessResult(service.processExpiredAsManager(id, token));
    }

    public record PolicyRequest(String policyVersion, int retentionDays,
        Instant effectiveAt, String approvalReference) {}
    public record ProcessResult(int processedCount) {}
}
