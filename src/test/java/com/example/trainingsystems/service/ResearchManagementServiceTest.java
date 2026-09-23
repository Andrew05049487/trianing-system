package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import com.example.trainingsystems.entity.ResearchConsentEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchConsentRepository;
import com.example.trainingsystems.repository.ResearchExportAuditRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchManagementServiceTest {
    @Mock ResearchAuthorityService authority;
    @Mock ResearchAnnotationRepository annotations;
    @Mock ResearchSampleRepository samples;
    @Mock ResearchConsentRepository consents;
    @Mock ResearchExportAuditRepository audits;
    private final ObjectMapper mapper = new ObjectMapper();
    private ResearchManagementService service;
    private User manager;

    @BeforeEach void setUp() {
        manager = new User();
        manager.setId(9L);
        service = new ResearchManagementService(authority, annotations, samples, consents,
            audits, new ResearchSampleValidator(mapper), new ResearchTrainingFeatureValidator(),
            mapper, "study-v1");
    }

    @Test void managerPermissionIsRequiredForExport() {
        when(authority.authenticated(9L, "token")).thenReturn(manager);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
            .when(authority).requireManager(manager);
        assertThatThrownBy(() -> service.exportApproved(9L, "token"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test void approvedConsentSampleExportsPythonCompatibleZipWithoutAccountIds() throws Exception {
        when(authority.authenticated(9L, "token")).thenReturn(manager);
        var annotation = approved();
        when(annotations.findByStatus(eq("APPROVED"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(annotation)));
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample()));
        when(consents.findById(1L)).thenReturn(Optional.of(consent(true)));
        byte[] zip = service.exportApproved(9L, "token");
        String labels = entry(zip, "labels.csv");
        assertThat(labels).contains("sample-1,meets_requirement,annotator_1,research-v1,standing-knee-raise-v1");
        assertThat(labels).doesNotContain("12345");
        assertThat(entry(zip, "samples/sample-1.json")).contains("\"subjectId\":\"subject-1\"");
        assertThat(entry(zip, "manifest.json")).contains("\"sampleCount\":1");
        verify(audits).save(any());
    }

    @Test void withdrawnOrUnassessableSamplesNeverExport() throws Exception {
        when(authority.authenticated(9L, "token")).thenReturn(manager);
        var approved = approved();
        when(annotations.findByStatus(eq("APPROVED"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(approved)));
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample()));
        when(consents.findById(1L)).thenReturn(Optional.of(consent(false)));
        assertThat(entry(service.exportApproved(9L, "token"), "manifest.json"))
            .contains("\"sampleCount\":0");
    }

    @Test void approvedOlderSampleUsesStoredValidationNotNewUploadAgeGate() throws Exception {
        when(authority.authenticated(9L, "token")).thenReturn(manager);
        when(annotations.findByStatus(eq("APPROVED"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(approved())));
        ResearchSampleEntity older = sample();
        ObjectNode payload = (ObjectNode) mapper.readTree(older.getPayloadJson());
        payload.put("capturedAt", Instant.now().minusSeconds(400L * 86400).toString());
        older.setPayloadJson(mapper.writeValueAsString(payload));
        when(samples.findById("sample-1")).thenReturn(Optional.of(older));
        when(consents.findById(1L)).thenReturn(Optional.of(consent(true)));
        assertThat(entry(service.exportApproved(9L, "token"), "manifest.json"))
            .contains("\"sampleCount\":1");
    }

    private ResearchAnnotationEntity approved() {
        var a = new ResearchAnnotationEntity();
        a.setSampleId("sample-1");
        a.setTherapistUserId(12345L);
        a.setReviewerUserId(8L);
        a.setReviewedAt(Instant.now());
        a.setStatus("APPROVED");
        a.setLabel("meets_requirement");
        a.setLabelVersion("research-v1");
        a.setActionDefinitionVersion("standing-knee-raise-v1");
        return a;
    }

    private ResearchConsentEntity consent(boolean active) {
        var c = new ResearchConsentEntity();
        c.setUserId(1L);
        c.setSubjectId("subject-1");
        c.setConsentVersion("study-v1");
        c.setActive(active);
        return c;
    }

    private ResearchSampleEntity sample() throws Exception {
        var sample = new ResearchSampleEntity();
        sample.setId("sample-1");
        sample.setParticipantUserId(1L);
        sample.setSubjectId("subject-1");
        sample.setExpiresAt(Instant.now().plusSeconds(86400));
        ObjectNode json = mapper.createObjectNode();
        json.put("schemaVersion", 1);
        json.put("actionId", "standing_knee_raise");
        json.put("sampleId", "sample-1");
        json.put("subjectId", "subject-1");
        json.put("movementSide", "left");
        json.put("cameraView", "front");
        json.put("capturedAt", Instant.now().toString());
        json.putObject("segment").put("startMs", 0).put("endMs", 300);
        ArrayNode names = json.putArray("featureNames");
        for (String name : List.of("peak_leg_height", "minimum_hip_angle_deg",
            "minimum_knee_angle_deg", "peak_abs_trunk_lean_deg", "duration_seconds")) names.add(name);
        ArrayNode values = json.putArray("features");
        for (double value : new double[]{-1, 180, 180, 0, 0.3}) values.add(value);
        ArrayNode frames = json.putArray("frames");
        for (int t = 0; t <= 300; t += 100) {
            ObjectNode frame = frames.addObject();
            frame.put("timestampMs", t);
            ArrayNode points = frame.putArray("landmarks");
            for (int i = 0; i < 17; i++) {
                double x = i == 6 || i == 12 || i == 14 || i == 16 ? 2 : 0;
                double y = i == 11 || i == 12 ? 1 :
                    i == 13 || i == 14 ? 2 : i == 15 || i == 16 ? 3 : 0;
                points.addArray().add(x).add(y);
            }
            ArrayNode confidence = frame.putArray("confidence");
            for (int i = 0; i < 17; i++) confidence.add(0.9);
            frame.putObject("angles").put("hipDeg", 180).put("kneeDeg", 180)
                .put("trunkLeanDeg", 0);
        }
        sample.setPayloadJson(mapper.writeValueAsString(json));
        return sample;
    }

    private String entry(byte[] zip, String name) throws Exception {
        try (var stream = new ZipInputStream(new ByteArrayInputStream(zip));
             var bytes = new ByteArrayOutputStream()) {
            for (var next = stream.getNextEntry(); next != null; next = stream.getNextEntry()) {
                bytes.reset();
                stream.transferTo(bytes);
                if (name.equals(next.getName())) return bytes.toString(StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("missing " + name);
    }
}
