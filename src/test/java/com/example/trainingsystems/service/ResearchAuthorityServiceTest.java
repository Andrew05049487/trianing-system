package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchGrantEntity;
import com.example.trainingsystems.entity.ResearchReviewRequestEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.ResearchGrantAuditRepository;
import com.example.trainingsystems.repository.ResearchGrantRepository;
import com.example.trainingsystems.repository.ResearchReviewRequestRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchAuthorityServiceTest {
    @Mock UserRepository users;
    @Mock CustomExerciseIdentityService identity;
    @Mock UserBindingRepository bindings;
    @Mock ResearchGrantRepository grants;
    @Mock ResearchReviewRequestRepository requests;
    @Mock ResearchGrantAuditRepository audits;
    ResearchAuthorityService service;

    @BeforeEach void setUp() {
        service = new ResearchAuthorityService(users, identity, bindings, grants, requests, audits);
    }

    User authenticated(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(identity.isConfigured()).thenReturn(true);
        when(identity.isValid(user, "token")).thenReturn(true);
        return user;
    }

    void managerGrant(long id) {
        ResearchGrantEntity grant = new ResearchGrantEntity();
        grant.setUserId(id);
        grant.setStudyId(ResearchAuthorityService.STUDY_ID);
        grant.setCanManage(true);
        when(grants.findByUserIdAndStudyId(id, ResearchAuthorityService.STUDY_ID))
            .thenReturn(Optional.of(grant));
    }

    @Test void invalidIdentityAndPatientCannotElevate() {
        assertThatThrownBy(() -> service.me(1L, "bad"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        authenticated(1L, "PATIENT");
        assertThatThrownBy(() -> service.requestReview(1L, "token"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verify(grants, never()).save(any());
    }

    @Test void therapistWithoutBindingCannotRequestReview() {
        authenticated(2L, "THERAPIST");
        assertThatThrownBy(() -> service.requestReview(2L, "token"))
            .isInstanceOf(ResponseStatusException.class);
        verify(requests, never()).save(any());
    }

    @Test void boundTherapistCanRequestButCannotApproveSelf() {
        authenticated(2L, "THERAPIST");
        when(bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(2L, "THERAPIST"))
            .thenReturn(List.of(new UserBinding()));
        var submitted = service.requestReview(2L, "token");
        assertThat(submitted.status()).isEqualTo("PENDING");
        ResearchReviewRequestEntity request = new ResearchReviewRequestEntity();
        request.setId(11L);
        request.setStudyId(ResearchAuthorityService.STUDY_ID);
        request.setUserId(2L);
        request.setStatus("PENDING");
        request.setRequestedAt(Instant.now());
        when(requests.findById(11L)).thenReturn(Optional.of(request));
        managerGrant(2L);
        assertThatThrownBy(() -> service.decide(2L, "token", 11L, true))
            .isInstanceOf(ResponseStatusException.class);
        verify(grants, never()).save(any());
    }

    @Test void managerApprovalGrantsReviewToOtherBoundTherapist() {
        authenticated(10L, "PATIENT");
        managerGrant(10L);
        User target = new User();
        target.setId(2L);
        target.setRole("THERAPIST");
        when(users.findById(2L)).thenReturn(Optional.of(target));
        ResearchReviewRequestEntity request = new ResearchReviewRequestEntity();
        request.setId(11L);
        request.setUserId(2L);
        request.setStudyId(ResearchAuthorityService.STUDY_ID);
        request.setStatus("PENDING");
        request.setRequestedAt(Instant.now());
        when(requests.findById(11L)).thenReturn(Optional.of(request));
        when(bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(2L, "THERAPIST"))
            .thenReturn(List.of(new UserBinding()));
        assertThat(service.decide(10L, "token", 11L, true).status()).isEqualTo("APPROVED");
        org.mockito.ArgumentCaptor<ResearchGrantEntity> saved =
            org.mockito.ArgumentCaptor.forClass(ResearchGrantEntity.class);
        verify(grants).save(saved.capture());
        assertThat(saved.getValue().isCanReview()).isTrue();
        assertThat(saved.getValue().isCanManage()).isFalse();
        verify(audits).save(any());
    }

    @Test void normalTherapistCannotAssignManagementGrant() {
        authenticated(2L, "THERAPIST");
        assertThatThrownBy(() -> service.setGrant(2L, "token", 2L, false, false, true))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verify(grants, never()).save(any());
    }

    @Test void managerCannotRemoveLastManagerOrGrantReviewToPatient() {
        authenticated(10L, "PATIENT");
        managerGrant(10L);
        when(grants.countByStudyIdAndCanManageTrue(ResearchAuthorityService.STUDY_ID))
            .thenReturn(1L);
        assertThatThrownBy(() -> service.setGrant(10L, "token", 10L, false, false, false))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.setGrant(10L, "token", 10L, false, true, true))
            .isInstanceOf(ResponseStatusException.class);
        verify(grants, never()).save(any());
    }
}
