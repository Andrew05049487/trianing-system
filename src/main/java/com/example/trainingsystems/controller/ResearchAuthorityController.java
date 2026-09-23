package com.example.trainingsystems.controller;

import com.example.trainingsystems.service.ResearchAuthorityService;
import com.example.trainingsystems.service.ResearchAuthorityService.AuthorityView;
import com.example.trainingsystems.service.ResearchAuthorityService.ReviewRequestView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ml-research/authority")
public class ResearchAuthorityController {
    private final ResearchAuthorityService service;

    public ResearchAuthorityController(ResearchAuthorityService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public AuthorityView me(@RequestHeader(value = "X-User-Id", required = false) Long id,
                            @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token) {
        return service.me(id, token);
    }

    @PostMapping("/review-request")
    public ReviewRequestView requestReview(@RequestHeader(value = "X-User-Id", required = false) Long id,
                                           @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token) {
        return service.requestReview(id, token);
    }

    @GetMapping("/review-requests")
    public List<ReviewRequestView> pending(@RequestHeader(value = "X-User-Id", required = false) Long id,
                                           @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token) {
        return service.pendingRequests(id, token);
    }

    @PutMapping("/review-requests/{requestId}")
    public ReviewRequestView decide(@RequestHeader(value = "X-User-Id", required = false) Long id,
                                    @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
                                    @PathVariable Long requestId, @RequestBody DecisionRequest request) {
        return service.decide(id, token, requestId, request.approve());
    }

    @PutMapping("/grants/{targetUserId}")
    public AuthorityView setGrant(@RequestHeader(value = "X-User-Id", required = false) Long id,
                                  @RequestHeader(value = "X-Custom-Exercise-Token", required = false) String token,
                                  @PathVariable Long targetUserId, @RequestBody GrantRequest request) {
        return service.setGrant(id, token, targetUserId,
            request.canAnnotate(), request.canReview(), request.canManage());
    }

    public record DecisionRequest(boolean approve) {}
    public record GrantRequest(boolean canAnnotate, boolean canReview, boolean canManage) {}
}
