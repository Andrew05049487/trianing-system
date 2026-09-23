package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchRetentionEventEntity;
import com.example.trainingsystems.entity.ResearchRetentionPolicyEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchAnnotationRevisionRepository;
import com.example.trainingsystems.repository.ResearchRetentionEventRepository;
import com.example.trainingsystems.repository.ResearchRetentionPolicyRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ResearchRetentionService {
    private final ResearchAuthorityService authority;
    private final ResearchRetentionPolicyRepository policies;
    private final ResearchRetentionEventRepository events;
    private final ResearchSampleRepository samples;
    private final ResearchAnnotationRepository annotations;
    private final ResearchAnnotationRevisionRepository revisions;

    public ResearchRetentionService(ResearchAuthorityService authority,
        ResearchRetentionPolicyRepository policies, ResearchRetentionEventRepository events,
        ResearchSampleRepository samples, ResearchAnnotationRepository annotations,
        ResearchAnnotationRevisionRepository revisions) {
        this.authority = authority;
        this.policies = policies;
        this.events = events;
        this.samples = samples;
        this.annotations = annotations;
        this.revisions = revisions;
    }

    public Optional<ResearchRetentionPolicyEntity> currentPolicy() {
        return policies.findTopByStudyIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            ResearchAuthorityService.STUDY_ID, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PolicyView> history(Long actorId, String token) {
        authority.requireManager(authority.authenticated(actorId, token));
        return policies.findByStudyIdOrderByCreatedAtDesc(ResearchAuthorityService.STUDY_ID)
            .stream().map(this::view).toList();
    }

    @Transactional
    public PolicyView createPolicy(Long actorId, String token, String version,
        int retentionDays, Instant effectiveAt, String approvalReference) {
        authority.requireManager(authority.authenticated(actorId, token));
        if (version == null || !version.matches("[A-Za-z0-9_.-]{1,64}") ||
            retentionDays < 1 || retentionDays > 36500 || effectiveAt == null ||
            approvalReference == null || !approvalReference.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_RETENTION_POLICY");
        }
        if (policies.existsByStudyIdAndPolicyVersion(ResearchAuthorityService.STUDY_ID, version)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RETENTION_POLICY_VERSION_EXISTS");
        }
        ResearchRetentionPolicyEntity policy = new ResearchRetentionPolicyEntity();
        policy.setStudyId(ResearchAuthorityService.STUDY_ID);
        policy.setPolicyVersion(version);
        policy.setRetentionDays(retentionDays);
        policy.setEffectiveAt(effectiveAt);
        policy.setConfiguredByUserId(actorId);
        policy.setApprovalReference(approvalReference);
        policy.setExpiryAction("DELETE");
        policy.setCreatedAt(Instant.now());
        policies.saveAndFlush(policy);
        return view(policy);
    }

    @Transactional
    public int processExpiredAsManager(Long actorId, String token) {
        authority.requireManager(authority.authenticated(actorId, token));
        return processExpiredBatch(Instant.now(), actorId);
    }

    /** Daily UTC batch; policy-less legacy rows are not silently treated as permanent or deleted. */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void runScheduled() {
        processExpiredBatch(Instant.now(), null);
    }

    int processExpiredBatch(Instant now, Long actorId) {
        List<ResearchSampleEntity> expired = samples.findExpiredForUpdate(now, PageRequest.of(0, 100));
        for (ResearchSampleEntity sample : expired) {
            revisions.deleteAll(revisions.findBySampleId(sample.getId()));
            annotations.findById(sample.getId()).ifPresent(annotations::delete);
            samples.delete(sample);
            recordDeletion(sample, "EXPIRED", actorId);
        }
        return expired.size();
    }

    public void recordDeletion(ResearchSampleEntity sample, String reason, Long actorId) {
        ResearchRetentionEventEntity event = new ResearchRetentionEventEntity();
        event.setSampleId(sample.getId());
        event.setPolicyVersion(sample.getRetentionPolicyVersion());
        event.setReason(reason);
        event.setActorUserId(actorId);
        event.setProcessedAt(Instant.now());
        events.save(event);
    }

    private PolicyView view(ResearchRetentionPolicyEntity policy) {
        return new PolicyView(policy.getStudyId(), policy.getPolicyVersion(),
            policy.getRetentionDays(), policy.getEffectiveAt(), policy.getApprovalReference(),
            policy.getExpiryAction(), policy.getConfiguredByUserId(), policy.getCreatedAt());
    }

    public record PolicyView(String studyId, String policyVersion, int retentionDays,
        Instant effectiveAt, String approvalReference, String expiryAction,
        Long configuredByUserId, Instant createdAt) {}
}
