package com.example.trainingsystems.service;

import com.example.trainingsystems.config.ResearchManagerInitializationRunner;
import com.example.trainingsystems.entity.ResearchGrantEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ResearchGrantAuditRepository;
import com.example.trainingsystems.repository.ResearchGrantRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchManagerInitializationServiceTest {
    @Mock UserRepository users;
    @Mock ResearchGrantRepository grants;
    @Mock ResearchGrantAuditRepository audits;
    ResearchManagerInitializationService service;
    User target;

    @BeforeEach void setUp() {
        service = new ResearchManagerInitializationService(users, grants, audits);
        target = new User();
        target.setId(256L);
        target.setRole("THERAPIST");
        target.setEmail("demo_manager@demo.invalid");
    }

    @Test void defaultRunnerDoesNothing() {
        new ResearchManagerInitializationRunner(service, "", "", "").run(null);
        verifyNoInteractions(users, grants, audits);
    }

    @Test void springCreatesTheDefaultOffRunner() {
        new ApplicationContextRunner()
            .withBean(ResearchManagerInitializationService.class, () -> service)
            .withUserConfiguration(ResearchManagerInitializationRunner.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                context.getBean(ResearchManagerInitializationRunner.class).run(null);
                verifyNoInteractions(users, grants, audits);
            });
    }

    @Test void verifiedFirstManagerIsAuditedWithoutChangingRole() {
        when(users.findById(256L)).thenReturn(Optional.of(target));
        service.initialize(256L, target.getEmail(), "OWNER-AUTH-20261003-G5");
        verify(grants).saveAndFlush(argThat(g -> g.getUserId() == 256L && g.isCanManage()
            && !g.isCanAnnotate() && !g.isCanReview()));
        verify(audits).saveAndFlush(argThat(a -> a.getTargetUserId() == 256L
            && "OWNER-AUTH-20261003-G5".equals(a.getAction())));
        assertThat(target.getRole()).isEqualTo("THERAPIST");
        verify(users, never()).save(any());
    }

    @Test void mismatchedEmailCannotInitialize() {
        when(users.findById(256L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.initialize(256L, "other@demo.invalid", "OWNER-TEST"))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(grants, audits);
    }

    @Test void patientCannotInitialize() {
        target.setRole("PATIENT");
        when(users.findById(256L)).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.initialize(256L, target.getEmail(), "OWNER-TEST"))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(grants, audits);
    }

    @Test void anotherManagerPreventsInitialization() {
        when(users.findById(256L)).thenReturn(Optional.of(target));
        when(grants.existsByStudyIdAndCanManageTrue(ResearchAuthorityService.STUDY_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.initialize(256L, target.getEmail(), "OWNER-TEST"))
            .isInstanceOf(IllegalStateException.class);
        verify(grants, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test void sameInitializedTargetIsIdempotent() {
        when(users.findById(256L)).thenReturn(Optional.of(target));
        ResearchGrantEntity existing = new ResearchGrantEntity();
        existing.setCanManage(true);
        when(grants.findByUserIdAndStudyId(256L, ResearchAuthorityService.STUDY_ID)).thenReturn(Optional.of(existing));
        service.initialize(256L, target.getEmail(), "OWNER-TEST");
        verify(grants, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test void incompleteOrInvalidOperatorSettingsFailClosed() {
        assertThatThrownBy(() -> new ResearchManagerInitializationRunner(service, "", "target", "ref").run(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.initialize(256L, "", "OWNER-TEST"))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users, grants, audits);
    }
}
