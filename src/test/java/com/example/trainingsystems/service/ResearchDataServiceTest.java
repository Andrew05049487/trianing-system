package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import com.example.trainingsystems.entity.ResearchConsentEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
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
    @Mock ResearchAuditRepository audits;
    private final ObjectMapper mapper = new ObjectMapper();
    private ResearchDataService service;
    private ResearchSampleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResearchSampleValidator(mapper);
        service = new ResearchDataService(users, bindings, identity, consents,
            samples, annotations, audits, validator, mapper, true, "study-v1");
    }

    private void authenticate(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(identity.isConfigured()).thenReturn(true);
        when(identity.isValid(user, "token")).thenReturn(true);
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

    @Test void patientConsentCreatesServerPseudonym() {
        authenticate(1L, "PATIENT");
        when(consents.findById(1L)).thenReturn(Optional.empty());
        var result = service.setConsent(1L, "token", true, "study-v1");
        assertThat(result.active()).isTrue();
        assertThat(result.subjectId()).isNotBlank();
        verify(consents).save(any());
        verify(audits).save(any());
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
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        when(consents.findById(1L)).thenReturn(Optional.of(activeConsent()));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L, 7L, "THERAPIST"))
            .thenReturn(true);
        when(annotations.findById("sample-1")).thenReturn(Optional.empty());
        var result = service.label(7L, "token", "sample-1",
            "unassessable", "2D skeleton insufficient", "v1", "v1");
        assertThat(result.label()).isEqualTo("unassessable");
        verify(annotations).save(any());
        ResearchAnnotationEntity other = new ResearchAnnotationEntity();
        other.setTherapistUserId(8L);
        when(annotations.findById("sample-1")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.label(7L, "token", "sample-1",
            "meets_requirement", "", "v1", "v1"))
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
