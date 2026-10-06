package com.example.trainingsystems.controller;

import com.example.trainingsystems.service.ResearchDataService;
import com.example.trainingsystems.service.ResearchDataService.AnnotationView;
import com.example.trainingsystems.service.ResearchDataService.ConsentView;
import com.example.trainingsystems.service.ResearchDataService.SampleDetail;
import com.example.trainingsystems.service.ResearchDataService.SampleView;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ml-research")
public class ResearchDataController {
    private final ResearchDataService service;

    public ResearchDataController(ResearchDataService service) { this.service = service; }

    @GetMapping("/consent")
    public ConsentView consent(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token
    ) { return service.consent(userId, token); }

    @PutMapping("/consent")
    public ConsentView setConsent(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @RequestBody ConsentRequest request
    ) { return service.setConsent(userId, token, request.agree(), request.version()); }

    @PostMapping("/samples")
    public SampleView upload(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @RequestBody JsonNode payload
    ) { return service.upload(userId, token, payload); }

    @GetMapping("/samples")
    public Page<SampleView> list(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { return service.list(userId, token, page, size); }

    @GetMapping("/samples/{sampleId}")
    public SampleDetail detail(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @PathVariable String sampleId
    ) { return service.detail(userId, token, sampleId); }

    @PutMapping("/samples/{sampleId}/label")
    public AnnotationView label(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @PathVariable String sampleId,
        @RequestBody LabelRequest request
    ) {
        return service.label(userId, token, sampleId, request.label(), request.note(),
            request.labelVersion(), request.actionDefinitionVersion(), request.expectedRevision());
    }

    @PostMapping("/samples/{sampleId}/label/submit")
    public AnnotationView submitLabel(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @PathVariable String sampleId,
        @RequestBody(required=false) SubmitRequest request
    ) { return service.submitLabel(userId, token, sampleId, request==null?null:request.expectedRevision()); }

    @GetMapping("/review-queue")
    public Page<SampleView> reviewQueue(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { return service.reviewQueue(userId, token, page, size); }

    @PostMapping("/samples/{sampleId}/label/review")
    public AnnotationView reviewLabel(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @PathVariable String sampleId,
        @RequestBody ReviewRequest request
    ) { return service.reviewLabel(userId, token, sampleId,
        request.decision()==null?(request.approve()?"APPROVE":"RETURN"):request.decision(),
        request.note(),request.reasonCode(),request.expectedRevision()); }

    @DeleteMapping("/samples/{sampleId}")
    public ResponseEntity<Void> deleteOwnSample(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
        @PathVariable String sampleId
    ) {
        service.deleteOwnSample(userId, token, sampleId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/my-data")
    public ResponseEntity<Void> deleteMyData(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token
    ) {
        service.deleteMyData(userId, token);
        return ResponseEntity.noContent().build();
    }

    public record ConsentRequest(boolean agree, String version) {}
    public record LabelRequest(String label, String note, String labelVersion,
                               String actionDefinitionVersion, Integer expectedRevision) {}
    public record SubmitRequest(Integer expectedRevision) {}
    public record ReviewRequest(boolean approve, String note, String decision,
                                String reasonCode, Integer expectedRevision) {}
}
