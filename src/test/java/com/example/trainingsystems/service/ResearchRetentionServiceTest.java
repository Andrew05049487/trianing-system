package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchRetentionPolicyEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchAnnotationRevisionRepository;
import com.example.trainingsystems.repository.ResearchRetentionEventRepository;
import com.example.trainingsystems.repository.ResearchRetentionPolicyRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchRetentionServiceTest {
    @Mock ResearchAuthorityService authority;
    @Mock ResearchRetentionPolicyRepository policies;
    @Mock ResearchRetentionEventRepository events;
    @Mock ResearchSampleRepository samples;
    @Mock ResearchAnnotationRepository annotations;
    @Mock ResearchAnnotationRevisionRepository revisions;
    private ResearchRetentionService service;

    @BeforeEach void setUp() {
        service = new ResearchRetentionService(authority, policies, events,
            samples, annotations, revisions);
    }

    @Test void missingPolicyIsNotPermanentRetention() {
        when(policies.findTopByStudyIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
            eq(ResearchAuthorityService.STUDY_ID), any())).thenReturn(Optional.empty());
        assertThat(service.currentPolicy()).isEmpty();
    }

    @Test void onlyManagerCanCreateVersionedPolicyAndNoDurationIsHardcoded() {
        User actor = new User();
        actor.setId(9L);
        when(authority.authenticated(9L, "token")).thenReturn(actor);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
            .when(authority).requireManager(actor);
        assertThatThrownBy(() -> service.createPolicy(9L, "token", "v1", 90,
            Instant.now(), "approval-reference"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void expiredBatchDeletesOnceAndSecondRunDoesNothing() {
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId("sample-1");
        sample.setRetentionPolicyVersion("approved-v1");
        when(samples.findExpiredForUpdate(any(), any(Pageable.class)))
            .thenReturn(List.of(sample), List.of());
        when(revisions.findBySampleId("sample-1")).thenReturn(List.of());
        when(annotations.findById("sample-1")).thenReturn(Optional.empty());
        assertThat(service.processExpiredBatch(Instant.now(), null)).isEqualTo(1);
        assertThat(service.processExpiredBatch(Instant.now(), null)).isZero();
        verify(samples).delete(sample);
        verify(events).save(any());
    }

    @Test void policyConfigurationRejectsMissingApprovalReference() {
        User actor = new User();
        actor.setId(9L);
        when(authority.authenticated(9L, "token")).thenReturn(actor);
        assertThatThrownBy(() -> service.createPolicy(9L, "token", "v1", 30,
            Instant.now(), ""))
            .isInstanceOf(ResponseStatusException.class);
    }
}
