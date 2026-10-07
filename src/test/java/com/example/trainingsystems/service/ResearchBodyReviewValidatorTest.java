package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ResearchBodyReviewValidatorTest {
    private static final ObjectMapper M = new ObjectMapper();

    static ObjectNode fixture(String action) {
        ObjectNode sample = ResearchBodyAttemptTest.fixture();
        sample.put("schemaVersion", 4);
        sample.put("actionId", action);
        sample.put("actionDefinitionVersion", ResearchActionRegistry.BODY_REVIEW_VERSIONS.get(action));
        sample.remove("extractorVersion");
        sample.remove("modelInputVersion");
        sample.put("featuresStatus", "not_applicable");
        sample.set("features", M.createArrayNode());
        sample.set("featureNames", M.createArrayNode());
        sample.put("terminationReason", "SCORED_REP");
        sample.put("completedRepsAfter", 1);
        if (Set.of("raise_both_arms", "elbow_forward", "sit_to_stand").contains(action))
            sample.put("movementSide", "bilateral");
        if ("lateral_step".equals(action)) sample.put("movementMode", "simple");
        for (var frame : sample.withArray("frames")) ((ObjectNode) frame).remove("angles");
        return sample;
    }

    @Test void allSixReviewActionsHaveDistinctExactVersionsAndNoTrainableLabels() {
        var validator = new ResearchSampleValidator(M);
        assertThat(ResearchActionRegistry.BODY_REVIEW_VERSIONS).hasSize(6);
        assertThat(ResearchActionRegistry.BODY_REVIEW_VERSIONS.values()).doesNotHaveDuplicates();
        for (String action : ResearchActionRegistry.BODY_REVIEW_VERSIONS.keySet()) {
            var sample = validator.validate(fixture(action));
            assertThat(sample.safePayload().path("actionId").asText()).isEqualTo(action);
            assertThat(validator.definition(sample.safePayload()).trainableLabels()).isEmpty();
            assertThat(sample.safePayload().path("features").isEmpty()).isTrue();
        }
        assertThat(validator.definition(ResearchBodyAttemptTest.fixture()).schemaVersion()).isEqualTo(3);
    }

    @Test void unsafeOrInconsistentReviewSamplesFailClosed() {
        var sample = fixture("draw_circle"); sample.put("patientId", 1); reject(sample);
        sample = fixture("draw_circle"); sample.put("subjectId", "client-identity"); reject(sample);
        sample = fixture("draw_circle"); sample.put("actionDefinitionVersion", "wrong"); reject(sample);
        sample = fixture("draw_circle"); sample.put("actionId", "body_skeleton_test"); reject(sample);
        sample = fixture("draw_circle"); sample.withArray("features").add(1); reject(sample);
        sample = fixture("draw_circle"); sample.put("movementSide", "bilateral"); reject(sample);
        sample = fixture("raise_both_arms"); sample.put("movementSide", "left"); reject(sample);
        sample = fixture("draw_circle"); ((ObjectNode) sample.withArray("frames").get(1)).put("timestampMs", 0); reject(sample);
        sample = fixture("draw_circle"); ((ArrayNode) sample.withArray("frames").get(1).path("validity")).set(5, BooleanNode.FALSE); reject(sample);
        sample = fixture("draw_circle"); ((ObjectNode) sample.withArray("frames").get(1)).put("imageWidth", 0); reject(sample);
    }

    @Test void storedServerPseudonymIsReadableButNeverTrustedOnUpload() {
        var sample = fixture("sit_to_stand"); sample.put("subjectId", "server-only");
        var validator = new ResearchSampleValidator(M);
        assertThat(validator.validateStored(sample).safePayload().has("subjectId")).isFalse();
        reject(sample);
    }

    @Test void persistedAssignmentMustMatchActionAndCustomFailsClosed() {
        var assignments = mock(ExerciseAssignmentRepository.class);
        var bindings = mock(UserBindingRepository.class);
        var guard = new ResearchBodyAssignmentService(assignments, bindings);
        var assignment = new ExerciseAssignmentEntity();
        var exercise = new Exercise(); exercise.setId(99L); exercise.setExerciseName("畫圓訓練");
        assignment.setExercise(exercise);
        var therapist = new User(); therapist.setId(2L); assignment.setAssignedByTherapist(therapist);
        when(assignments.findByExercise_IdAndPatient_Id(99L, 1L)).thenReturn(Optional.of(assignment));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 2L, "THERAPIST")).thenReturn(true);
        guard.requireAssigned(1L, fixture("draw_circle"));
        assertThatThrownBy(() -> guard.requireAssigned(1L, fixture("sit_to_stand")))
            .isInstanceOf(ResponseStatusException.class);
        var custom = fixture("draw_circle"); custom.put("exerciseType", "CUSTOM");
        assertThatThrownBy(() -> guard.requireAssigned(1L, custom))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void scopeIsExplicitAndV4UploadUsesBodyOwnerAndAttemptDedupe() {
        var users = mock(UserRepository.class); var identity = mock(CustomExerciseIdentityService.class);
        var consents = mock(ResearchConsentRepository.class); var samples = mock(ResearchSampleRepository.class);
        var retention = mock(ResearchRetentionService.class); var assignment = mock(ResearchBodyAssignmentService.class);
        var service = new ResearchDataService(users, mock(UserBindingRepository.class), identity, consents,
            samples, mock(ResearchAnnotationRepository.class), mock(ResearchAnnotationRevisionRepository.class),
            mock(ResearchAuditRepository.class), new ResearchSampleValidator(M),
            mock(ResearchAuthorityService.class), retention, M, true, "test-v1");
        ReflectionTestUtils.setField(service, "bodyAssignments", assignment);
        var user = new User(); user.setId(1L); user.setRole("PATIENT");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(identity.isConfigured()).thenReturn(true); when(identity.isValid(user, "token")).thenReturn(true);
        var consent = new ResearchConsentEntity(); consent.setActive(true);
        consent.setConsentVersion("test-v1"); consent.setSubjectId("server-subject");
        when(consents.findForUpload(1L)).thenReturn(Optional.of(consent));
        var policy = new ResearchRetentionPolicyEntity(); policy.setPolicyVersion("test-policy");
        policy.setRetentionDays(1); when(retention.currentPolicy()).thenReturn(Optional.of(policy));
        var stored = new ArrayList<ResearchSampleEntity>();
        when(samples.saveAndFlush(any())).thenAnswer(invocation -> {
            ResearchSampleEntity entity = invocation.getArgument(0); stored.add(entity); return entity;
        });
        var payload = fixture("draw_circle");
        assertThatThrownBy(() -> service.upload(1L, "token", payload))
            .hasMessageContaining("BODY_RESEARCH_SCOPE_NOT_APPROVED");
        ReflectionTestUtils.setField(service, "bodyAvailableActions",
            String.join(",", ResearchBodyAssignmentService.DEFAULT_ACTIONS.values()));
        var first = service.upload(1L, "token", payload);
        assertThat(first.schemaVersion()).isEqualTo(4);
        assertThat(stored.get(0).getActionId()).isEqualTo("draw_circle");
        when(samples.findByParticipantUserIdAndAttemptId(1L, "attempt-1"))
            .thenReturn(Optional.of(stored.get(0)));
        assertThat(service.upload(1L, "token", payload).id()).isEqualTo(first.id());
        verify(assignment, times(2)).requireAssigned(eq(1L), any());
    }

    private static void reject(ObjectNode sample) {
        assertThatThrownBy(() -> new ResearchSampleValidator(M).validate(sample))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("INVALID_BODY_REVIEW_SAMPLE");
    }
}
