package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchConsentRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/** Called by the existing account deletion transaction before removing a User. */
@Service
public class ResearchAccountCleanupService {
    private final ResearchSampleRepository samples;
    private final ResearchAnnotationRepository annotations;
    private final ResearchConsentRepository consents;

    public ResearchAccountCleanupService(ResearchSampleRepository samples,
        ResearchAnnotationRepository annotations, ResearchConsentRepository consents) {
        this.samples = samples;
        this.annotations = annotations;
        this.consents = consents;
    }

    public void deleteForAccount(Long userId) {
        List<ResearchSampleEntity> owned = samples.findByParticipantUserId(userId);
        for (ResearchSampleEntity sample : owned) {
            annotations.findById(sample.getId()).ifPresent(annotations::delete);
        }
        samples.deleteAll(owned);
        annotations.deleteAll(annotations.findByTherapistUserId(userId));
        consents.findById(userId).ifPresent(consents::delete);
    }
}
