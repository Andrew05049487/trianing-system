package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import com.example.trainingsystems.entity.ResearchConsentEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.entity.ResearchRetentionPolicyEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchAnnotationRevisionRepository;
import com.example.trainingsystems.repository.ResearchAuditRepository;
import com.example.trainingsystems.repository.ResearchConsentRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchDataServiceTest {
    @Mock UserRepository users;
    @Mock UserBindingRepository bindings;
    @Mock CustomExerciseIdentityService identity;
    @Mock ResearchConsentRepository consents;
    @Mock ResearchSampleRepository samples;
    @Mock ResearchAnnotationRepository annotations;
    @Mock ResearchAnnotationRevisionRepository revisions;
    @Mock ResearchAuditRepository audits;
    @Mock ResearchAuthorityService authority;
    @Mock ResearchRetentionService retention;
    private final ObjectMapper mapper = new ObjectMapper();
    private ResearchDataService service;
    private ResearchSampleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResearchSampleValidator(mapper);
        service = new ResearchDataService(users, bindings, identity, consents,
            samples, annotations, revisions, audits, validator, authority,
            retention, mapper, true, "study-v1");
        ResearchRetentionPolicyEntity policy = new ResearchRetentionPolicyEntity();
        policy.setPolicyVersion("test-policy-v1");
        policy.setRetentionDays(30);
        org.mockito.Mockito.lenient().when(retention.currentPolicy()).thenReturn(Optional.of(policy));
    }

    @Test void handUploadRequiresNewApprovedScopeThenUsesSameDedupAndAnnotationContract() {
        authenticate(1L,"PATIENT");
        var consent=new ResearchConsentEntity(); consent.setActive(true); consent.setUserId(1L);
        consent.setConsentVersion("study-v1"); consent.setSubjectId("synthetic-subject");
        when(consents.findById(1L)).thenReturn(Optional.of(consent));
        var hand=ResearchHandContractTest.fixture("sidePinch");
        assertThatThrownBy(()->service.upload(1L,"token",hand)).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("HAND_RESEARCH_SCOPE_NOT_APPROVED");
        verify(samples,never()).saveAndFlush(any());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"handConsentVersion","study-v1");
        when(samples.findByParticipantUserIdAndClientSampleId(1L,"synthetic-hand")).thenReturn(Optional.empty());
        var result=service.upload(1L,"token",hand);
        assertThat(result.movementSide()).isEqualTo("unknown");
        assertThat(result.actionId()).isEqualTo("sidePinch");
        var captor=org.mockito.ArgumentCaptor.forClass(ResearchSampleEntity.class);
        verify(samples).saveAndFlush(captor.capture());
        var stored=captor.getValue();
        assertThat(stored.getPayloadJson()).contains("mediapipe_hand_21","synthetic-subject").doesNotContain("confidence");
        when(samples.findByParticipantUserIdAndClientSampleId(1L,"synthetic-hand")).thenReturn(Optional.of(stored));
        assertThat(service.upload(1L,"token",hand).id()).isEqualTo(stored.getId());
        verify(samples,org.mockito.Mockito.times(1)).saveAndFlush(any());
    }

    private void authenticate(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(identity.isConfigured()).thenReturn(true);
        when(identity.isValid(user, "token")).thenReturn(true);
        if ("THERAPIST".equals(role)) {
            org.mockito.Mockito.lenient().when(authority.canAnnotate(user)).thenReturn(true);
        }
    }

    private ResearchConsentEntity activeConsent() {
        ResearchConsentEntity consent = new ResearchConsentEntity();
        consent.setUserId(1L);
        consent.setSubjectId("11111111-1111-4111-8111-111111111111");
        consent.setConsentVersion("study-v1");
        consent.setActive(true);
        return consent;
    }

    private ObjectNode validPayload() {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("actionId", "standing_knee_raise");
        root.put("sampleId", "rep_test_001");
        root.put("subjectId", "local-code-not-trusted");
        root.put("movementSide", "left");
        root.put("cameraView", "front");
        root.put("capturedAt", Instant.now().toString());
        root.putObject("segment").put("startMs", 0).put("endMs", 300);
        ArrayNode names = root.putArray("featureNames");
        for (String name : List.of("peak_leg_height", "minimum_hip_angle_deg",
            "minimum_knee_angle_deg", "peak_abs_trunk_lean_deg", "duration_seconds")) names.add(name);
        ArrayNode features = root.putArray("features");
        for (double value : new double[]{-0.5, 180, 180, 0, 0.3}) features.add(value);
        ArrayNode frames = root.putArray("frames");
        for (int t = 0; t <= 300; t += 100) {
            ObjectNode frame = frames.addObject();
            frame.put("timestampMs", t);
            ArrayNode points = frame.putArray("landmarks");
            ArrayNode scores = frame.putArray("confidence");
            for (int i = 0; i < 17; i++) {
                ArrayNode point = points.addArray();
                point.add(0.1);
                point.add(0.2);
                scores.add(0.9);
            }
            frame.putObject("angles").put("hipDeg", 100).put("kneeDeg", 90)
                .put("trunkLeanDeg", 2);
        }
        return root;
    }

    @Test void legacyCanonicalPayloadAndActionContractsStaySeparate() {
        ObjectNode legacy = validPayload();
        var safe = validator.validate(legacy).safePayload();
        assertThat(safe.has("actionDefinitionVersion")).isFalse();
        assertThat(safe.path("frames").get(0).path("angles").toString())
            .isEqualTo("{\"hipDeg\":100.0,\"kneeDeg\":90.0,\"trunkLeanDeg\":2.0}");
        legacy.put("actionDefinitionVersion", "wrong-action-v1");
        assertThatThrownBy(() -> validator.validate(legacy)).isInstanceOf(ResponseStatusException.class);
        ObjectNode synthetic = validPayload();
        synthetic.put("actionId", "synthetic_test_only");
        synthetic.put("actionDefinitionVersion", "synthetic-v1");
        synthetic.putArray("featureNames").add("duration_seconds");
        synthetic.putArray("features").add(0.3);
        assertThatThrownBy(() -> validator.validate(synthetic)).isInstanceOf(ResponseStatusException.class);
        var testValidator = new ResearchSampleValidator(mapper, SyntheticResearchContract.REGISTRY);
        assertThat(new ResearchTrainingFeatureValidator(SyntheticResearchContract.REGISTRY)
            .matches(testValidator.validate(synthetic).safePayload())).isTrue();
        synthetic.putArray("featureNames").add("peak_leg_height");
        assertThatThrownBy(() -> testValidator.validate(synthetic)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void injectedSyntheticActionUsesExistingUploadAndLabelPipeline() throws Exception {
        validator = new ResearchSampleValidator(mapper, SyntheticResearchContract.REGISTRY);
        service = new ResearchDataService(users, bindings, identity, consents, samples,
            annotations, revisions, audits, validator, authority, retention, mapper, true, "study-v1");
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        ObjectNode payload = validPayload();
        payload.put("actionId", "synthetic_test_only");
        payload.put("actionDefinitionVersion", "synthetic-v1");
        payload.putArray("featureNames").add("duration_seconds");
        payload.putArray("features").add(0.3);
        when(samples.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var uploaded = service.upload(1L, "token", payload);
        assertThat(uploaded.actionId()).isEqualTo("synthetic_test_only");
        var capture = org.mockito.ArgumentCaptor.forClass(ResearchSampleEntity.class);
        verify(samples).saveAndFlush(capture.capture());
        var saved = capture.getValue();
        when(samples.findByParticipantUserIdAndClientSampleId(1L, "rep_test_001"))
            .thenReturn(Optional.of(saved));
        assertThat(service.upload(1L, "token", payload).id()).isEqualTo(uploaded.id());
        authenticate(7L, "THERAPIST");
        when(samples.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 7L, "THERAPIST"))
            .thenReturn(true);
        var label = service.label(7L, "token", saved.getId(), "test_match", "fixture", "test-v1", "synthetic-v1");
        assertThat(label.actionDefinitionVersion()).isEqualTo("synthetic-v1");
        assertThatThrownBy(() -> service.label(7L, "token", saved.getId(), "meets_requirement", "", "v1", "synthetic-v1"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void patientConsentCreatesServerPseudonym() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        var result = service.setConsent(1L, "token", true, "study-v1");
        assertThat(result.active()).isTrue();
        assertThat(result.subjectId()).isNotBlank();
        verify(consents).save(any());
        verify(audits).save(any());
    }

    @Test void cloudCollectionStaysClosedWithoutApprovedRetentionPolicy() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        when(retention.currentPolicy()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setConsent(1L, "token", true, "study-v1"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(consents, never()).save(any());
    }

    @Test void consentReportsClosedFlagWithoutCreatingConsent() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        var closed = new ResearchDataService(users, bindings, identity, consents,
            samples, annotations, revisions, audits, validator, authority,
            retention, mapper, false, "study-v1");
        assertThat(closed.consent(1L, "token").available()).isFalse();
        assertThat(closed.consent(1L, "token").unavailableReason())
            .isEqualTo("RESEARCH_COLLECTION_NOT_ENABLED");
        verify(consents, never()).save(any());
    }

    @Test void consentReportsUnsetVersionAndEffectivePolicySeparately() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        var unset = new ResearchDataService(users, bindings, identity, consents,
            samples, annotations, revisions, audits, validator, authority,
            retention, mapper, true, "");
        assertThat(unset.consent(1L, "token").unavailableReason())
            .isEqualTo("RESEARCH_CONSENT_VERSION_UNSET");
        when(retention.currentPolicy()).thenReturn(Optional.empty());
        assertThat(service.consent(1L, "token").unavailableReason())
            .isEqualTo("RESEARCH_RETENTION_UNSET");
        assertThatThrownBy(() -> service.setConsent(1L, "token", true, "study-v1"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getReason())
            .isEqualTo("RESEARCH_RETENTION_UNSET");
    }

    @Test void consentVersionMismatchRemainsRejectedWithoutSaving() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.consent(1L, "token").unavailableReason()).isNull();
        assertThatThrownBy(() -> service.setConsent(1L, "token", true, "obsolete"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getReason())
            .isEqualTo("CONSENT_VERSION_MISMATCH");
        verify(consents, never()).save(any());
    }

    @Test void invalidIdentityAndTherapistCannotConsent() {
        assertThatThrownBy(() -> service.consent(1L, "bad"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        authenticate(2L, "THERAPIST");
        assertThatThrownBy(() -> service.setConsent(2L, "token", true, "study-v1"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void uploadRequiresActiveConsent() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.upload(1L, "token", validPayload()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verify(samples, never()).saveAndFlush(any());
    }

    @Test void uploadSanitizesSubjectAndIsIdempotent() throws Exception {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        when(samples.findByParticipantUserIdAndClientSampleId(1L, "rep_test_001"))
            .thenReturn(Optional.empty());
        ObjectNode payload = validPayload();
        var first = service.upload(1L, "token", payload);
        org.mockito.ArgumentCaptor<ResearchSampleEntity> captured =
            org.mockito.ArgumentCaptor.forClass(ResearchSampleEntity.class);
        verify(samples).saveAndFlush(captured.capture());
        ResearchSampleEntity saved = captured.getValue();
        assertThat(first.id()).isEqualTo(saved.getId());
        JsonNode stored = mapper.readTree(saved.getPayloadJson());
        assertThat(stored.path("subjectId").asText()).isEqualTo(activeConsent().getSubjectId());
        assertThat(stored.path("subjectId").asText()).isNotEqualTo("local-code-not-trusted");
        when(samples.findByParticipantUserIdAndClientSampleId(1L, "rep_test_001"))
            .thenReturn(Optional.of(saved));
        assertThat(service.upload(1L, "token", payload).id()).isEqualTo(first.id());
        verify(samples, org.mockito.Mockito.times(1)).saveAndFlush(any());
    }

    @Test void invalidBodyIsRejectedBeforeStorage() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        ObjectNode payload = validPayload();
        payload.path("frames").get(0).path("confidence");
        ((ArrayNode) payload.path("frames").get(0).path("confidence")).set(13,
            mapper.getNodeFactory().numberNode(0.1));
        assertThatThrownBy(() -> service.upload(1L, "token", payload))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(samples, never()).saveAndFlush(any());
    }

    @Test void validatorRejectsUnexpectedPersonalFieldsAndBadShape() {
        ObjectNode withName = validPayload();
        withName.put("patientName", "not allowed");
        assertThatThrownBy(() -> validator.validate(withName))
            .isInstanceOf(ResponseStatusException.class);
        ObjectNode tooShort = validPayload();
        ((ArrayNode) tooShort.path("frames").get(0).path("landmarks")).remove(16);
        assertThatThrownBy(() -> validator.validate(tooShort))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void withdrawalStopsFurtherUploadWithoutDeletingExistingSamples() {
        authenticate(1L, "PATIENT");
        ResearchConsentEntity withdrawn = activeConsent();
        withdrawn.setActive(false);
        when(consents.findById(1L)).thenReturn(Optional.of(withdrawn));
        assertThatThrownBy(() -> service.upload(1L, "token", validPayload()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verify(samples, never()).saveAndFlush(any());
    }

    @Test void therapistSeesOnlyBoundActiveParticipants() {
        authenticate(7L, "THERAPIST");
        User patient = new User();
        patient.setId(1L);
        UserBinding binding = new UserBinding();
        binding.setPatient(patient);
        when(bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(7L, "THERAPIST"))
            .thenReturn(List.of(binding));
        when(consents.findByUserIdInAndActiveTrue(List.of(1L)))
            .thenReturn(List.of(activeConsent()));
        when(samples.findByParticipantUserIdIn(eq(List.of(1L)), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.list(7L, "token", 0, 20).getContent()).isEmpty();
        verify(samples).findByParticipantUserIdIn(eq(List.of(1L)), any(Pageable.class));
    }

    @Test void unrelatedTherapistCannotReadOrLabel() {
        authenticate(7L, "THERAPIST");
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId("sample-1");
        sample.setParticipantUserId(1L);
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        assertThatThrownBy(() -> service.detail(7L, "token", "sample-1"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.label(7L, "token", "sample-1",
            "meets_requirement", "", "v1", "v1"))
            .isInstanceOf(ResponseStatusException.class);
        verify(annotations, never()).save(any());
    }

    @Test void boundTherapistCanLabelButNotOverwriteAnotherAnnotator() {
        authenticate(7L, "THERAPIST");
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId("sample-1");
        sample.setParticipantUserId(1L);
        sample.setPayloadJson(validPayload().toString());
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 7L, "THERAPIST"))
            .thenReturn(true);
        when(annotations.findForUpdate("sample-1")).thenReturn(Optional.empty());
        var result = service.label(7L, "token", "sample-1",
            "unassessable", "2D skeleton insufficient", "v1", "standing-knee-raise-v1");
        assertThat(result.label()).isEqualTo("unassessable");
        verify(annotations).save(any());
        assertThatThrownBy(() -> service.label(7L, "token", "sample-1",
            "meets_requirement", "", "v1", "another-action-v1"))
            .isInstanceOf(ResponseStatusException.class);
        ResearchAnnotationEntity other = new ResearchAnnotationEntity();
        other.setTherapistUserId(8L);
        when(annotations.findForUpdate("sample-1")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.label(7L, "token", "sample-1",
            "meets_requirement", "", "v1", "standing-knee-raise-v1"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void patientCanDeleteOnlyOwnSample() {
        authenticate(1L, "PATIENT");
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId("sample-1");
        sample.setParticipantUserId(1L);
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        service.deleteOwnSample(1L, "token", "sample-1");
        verify(annotations).deleteById("sample-1");
        verify(samples).delete(sample);
    }

    @Test void draftMustBeSubmittedBeforeReviewAndSnapshotsAreWritten() {
        authenticate(7L, "THERAPIST");
        ResearchSampleEntity sample = sample("sample-1", 1L);
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 7L, "THERAPIST"))
            .thenReturn(true);
        ResearchAnnotationEntity draft = annotation("sample-1", 7L, "DRAFT");
        when(annotations.findForUpdate("sample-1")).thenReturn(Optional.of(draft));
        var submitted = service.submitLabel(7L, "token", "sample-1");
        assertThat(submitted.status()).isEqualTo("SUBMITTED");
        assertThat(submitted.submittedAt()).isNotNull();
        verify(revisions).save(any());
    }

    @Test void reviewerCannotApproveOwnSubmittedLabelEvenIfManager() {
        authenticate(7L, "THERAPIST");
        ResearchSampleEntity sample = sample("sample-1", 1L);
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 7L, "THERAPIST"))
            .thenReturn(true);
        var submitted = annotation("sample-1", 7L, "SUBMITTED");
        when(annotations.findForUpdate("sample-1")).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> service.reviewLabel(7L, "token", "sample-1", true, ""))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verify(annotations, never()).save(any());
    }

    @Test void approvedLabelIsLockedAndReturnRequiresReason() {
        authenticate(7L, "THERAPIST");
        ResearchSampleEntity sample = sample("sample-1", 1L);
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 7L, "THERAPIST"))
            .thenReturn(true);
        var submitted = annotation("sample-1", 8L, "SUBMITTED");
        when(annotations.findForUpdate("sample-1")).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> service.reviewLabel(7L, "token", "sample-1", false, "  "))
            .isInstanceOf(ResponseStatusException.class);
        var reviewed = service.reviewLabel(7L, "token", "sample-1", true, "符合標準");
        assertThat(reviewed.status()).isEqualTo("APPROVED");
        assertThat(reviewed.reviewerUserId()).isEqualTo(7L);
        assertThatThrownBy(() -> service.label(7L, "token", "sample-1",
            "meets_requirement", "", "v1", "v1"))
            .isInstanceOf(ResponseStatusException.class);
    }

    private ResearchSampleEntity sample(String id, Long participantId) {
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId(id);
        sample.setParticipantUserId(participantId);
        return sample;
    }

    private ResearchAnnotationEntity annotation(String sampleId, Long annotator, String status) {
        ResearchAnnotationEntity result = new ResearchAnnotationEntity();
        result.setSampleId(sampleId);
        result.setTherapistUserId(annotator);
        result.setStatus(status);
        result.setLabel("meets_requirement");
        result.setLabelVersion("v1");
        result.setActionDefinitionVersion("v1");
        return result;
    }

    @Test void patientCanDeleteAllOwnResearchData() {
        authenticate(1L, "PATIENT");
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId("sample-1");
        sample.setParticipantUserId(1L);
        ResearchAnnotationEntity annotation = new ResearchAnnotationEntity();
        when(samples.findByParticipantUserId(1L)).thenReturn(List.of(sample));
        when(annotations.findById("sample-1")).thenReturn(Optional.of(annotation));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        service.deleteMyData(1L, "token");
        verify(annotations).delete(annotation);
        verify(samples).deleteAll(List.of(sample));
        verify(consents).delete(any());
        verify(audits).save(any());
    }
}
