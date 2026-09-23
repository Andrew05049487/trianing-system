package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
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
    private final ResearchConsentRepository consents;
    private final ResearchGrantRepository grants;
    private final ResearchReviewRequestRepository reviewRequests;

    public ResearchAccountCleanupService(ResearchSampleRepository samples,
        ResearchAnnotationRepository annotations, ResearchConsentRepository consents,
        ResearchGrantRepository grants, ResearchReviewRequestRepository reviewRequests) {
        this.samples = samples;
        this.annotations = annotations;
        this.consents = consents;
        this.grants = grants;
        this.reviewRequests = reviewRequests;
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
        }
        samples.deleteAll(owned);
        annotations.deleteAll(annotations.findByTherapistUserId(userId));
        consents.findById(userId).ifPresent(consents::delete);
        reviewRequests.deleteAll(reviewRequests.findByUserId(userId));
        grants.deleteAll(grants.findByUserId(userId));
    }
}
