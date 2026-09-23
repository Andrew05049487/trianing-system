package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchAnnotationRevisionRepository;
import com.example.trainingsystems.repository.ResearchConsentRepository;
import com.example.trainingsystems.repository.ResearchGrantRepository;
import com.example.trainingsystems.repository.ResearchReviewRequestRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Called by the existing account deletion transaction before removing a User. */
@Service
public class ResearchAccountCleanupService {
    private final ResearchSampleRepository samples;
    private final ResearchAnnotationRepository annotations;
    private final ResearchAnnotationRevisionRepository revisions;
    private final ResearchConsentRepository consents;
    private final ResearchGrantRepository grants;
    private final ResearchReviewRequestRepository reviewRequests;
    private final ResearchRetentionService retention;

    public ResearchAccountCleanupService(ResearchSampleRepository samples,
        ResearchAnnotationRepository annotations, ResearchAnnotationRevisionRepository revisions,
        ResearchConsentRepository consents,
        ResearchGrantRepository grants, ResearchReviewRequestRepository reviewRequests,
        ResearchRetentionService retention) {
        this.samples = samples;
        this.annotations = annotations;
        this.revisions = revisions;
        this.consents = consents;
        this.grants = grants;
        this.reviewRequests = reviewRequests;
        this.retention = retention;
    }

    public void deleteForAccount(Long userId) {
        for (var grant : grants.findByUserId(userId)) {
            if (grant.isCanManage() &&
                grants.countByStudyIdAndCanManageTrue(grant.getStudyId()) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "RESEARCH_LAST_MANAGER");
            }
        }
        List<ResearchSampleEntity> owned = samples.findByParticipantUserId(userId);
        for (ResearchSampleEntity sample : owned) {
            annotations.findById(sample.getId()).ifPresent(annotations::delete);
            revisions.deleteAll(revisions.findBySampleId(sample.getId()));
            retention.recordDeletion(sample, "ACCOUNT_DELETED", userId);
        }
        samples.deleteAll(owned);
        for (var annotation : annotations.findByTherapistUserId(userId)) {
            revisions.deleteAll(revisions.findBySampleId(annotation.getSampleId()));
            annotations.delete(annotation);
        }
        consents.findById(userId).ifPresent(consents::delete);
        reviewRequests.deleteAll(reviewRequests.findByUserId(userId));
        grants.deleteAll(grants.findByUserId(userId));
    }
}
