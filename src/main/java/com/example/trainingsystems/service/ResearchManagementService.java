package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import com.example.trainingsystems.entity.ResearchConsentEntity;
import com.example.trainingsystems.entity.ResearchExportAuditEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchConsentRepository;
import com.example.trainingsystems.repository.ResearchExportAuditRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Bounded, in-memory export; never writes training files to Render's disk. */
@Service
public class ResearchManagementService {
    private static final Set<String> TRAINABLE_LABELS = Set.of(
        "meets_requirement", "insufficient_range", "trunk_compensation");
    private static final int MAX_SAMPLES = 500;
    private static final int MAX_UNCOMPRESSED_BYTES = 20_000_000;
    private final ResearchAuthorityService authority;
    private final ResearchAnnotationRepository annotations;
    private final ResearchSampleRepository samples;
    private final ResearchConsentRepository consents;
    private final ResearchExportAuditRepository audits;
    private final ResearchSampleValidator validator;
    private final ResearchTrainingFeatureValidator trainingFeatures;
    private final ObjectMapper mapper;
    private final String consentVersion;

    public ResearchManagementService(ResearchAuthorityService authority,
        ResearchAnnotationRepository annotations, ResearchSampleRepository samples,
        ResearchConsentRepository consents, ResearchExportAuditRepository audits,
        ResearchSampleValidator validator, ResearchTrainingFeatureValidator trainingFeatures,
        ObjectMapper mapper,
        @Value("${research.consent-version:}") String consentVersion) {
        this.authority = authority;
        this.annotations = annotations;
        this.samples = samples;
        this.consents = consents;
        this.audits = audits;
        this.validator = validator;
        this.trainingFeatures = trainingFeatures;
        this.mapper = mapper;
        this.consentVersion = consentVersion;
    }

    @Transactional(readOnly = true)
    public Stats stats(Long userId, String token) {
        authority.requireManager(authority.authenticated(userId, token));
        return new Stats(ResearchAuthorityService.STUDY_ID,
            samples.count(), annotations.count(),
            annotations.findByStatus("SUBMITTED", PageRequest.of(0, 1)).getTotalElements(),
            annotations.findByStatus("APPROVED", PageRequest.of(0, 1)).getTotalElements());
    }

    @Transactional
    public byte[] exportApproved(Long userId, String token) {
        authority.requireManager(authority.authenticated(userId, token));
        if (consentVersion.isBlank()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "RESEARCH_CONSENT_VERSION_UNSET");
        var page = annotations.findByStatus("APPROVED", PageRequest.of(0, MAX_SAMPLES + 1));
        if (page.getTotalElements() > MAX_SAMPLES) throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "RESEARCH_EXPORT_BATCH_TOO_LARGE");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StringBuilder labels = new StringBuilder("sampleId,label,annotatorId,labelVersion,actionDefinitionVersion\n");
        Map<Long, String> aliases = new HashMap<>();
        int count = 0;
        int uncompressed = 0;
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (ResearchAnnotationEntity annotation : page.getContent()) {
                if (!TRAINABLE_LABELS.contains(annotation.getLabel()) ||
                    annotation.getReviewerUserId() == null || annotation.getReviewedAt() == null ||
                    annotation.getReviewerUserId().equals(annotation.getTherapistUserId())) continue;
                ResearchSampleEntity sample = samples.findById(annotation.getSampleId()).orElse(null);
                if (sample == null) continue;
                if (sample.getExpiresAt() == null || !sample.getExpiresAt().isAfter(Instant.now())) continue;
                ResearchConsentEntity consent = consents.findById(sample.getParticipantUserId()).orElse(null);
                if (consent == null || !consent.isActive() ||
                    !consentVersion.equals(consent.getConsentVersion()) ||
                    !sample.getSubjectId().equals(consent.getSubjectId())) continue;
                JsonNode stored = mapper.readTree(sample.getPayloadJson());
                // Re-sanitize before export; never pass through unknown/direct identifiers.
                ObjectNode safe = validator.validate(stored).safePayload();
                safe.put("sampleId", sample.getId());
                safe.put("subjectId", sample.getSubjectId());
                if (!trainingFeatures.matches(safe)) continue;
                byte[] body = mapper.writeValueAsBytes(safe);
                uncompressed += body.length;
                if (uncompressed > MAX_UNCOMPRESSED_BYTES) throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "RESEARCH_EXPORT_TOO_LARGE");
                zip.putNextEntry(new ZipEntry("samples/" + sample.getId() + ".json"));
                zip.write(body);
                zip.closeEntry();
                String alias = aliases.computeIfAbsent(annotation.getTherapistUserId(),
                    ignored -> "annotator_" + (aliases.size() + 1));
                labels.append(sample.getId()).append(',').append(annotation.getLabel()).append(',')
                    .append(alias).append(',').append(annotation.getLabelVersion()).append(',')
                    .append(annotation.getActionDefinitionVersion()).append('\n');
                count++;
            }
            zip.putNextEntry(new ZipEntry("labels.csv"));
            zip.write(labels.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            ObjectNode manifest = mapper.createObjectNode();
            manifest.put("studyId", ResearchAuthorityService.STUDY_ID);
            manifest.put("schemaVersion", 1);
            manifest.put("sampleCount", count);
            manifest.put("exportedAt", Instant.now().toString());
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(mapper.writeValueAsBytes(manifest));
            zip.closeEntry();
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "RESEARCH_EXPORT_INVALID_SAMPLE");
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "RESEARCH_EXPORT_FAILED");
        }
        ResearchExportAuditEntity audit = new ResearchExportAuditEntity();
        audit.setActorUserId(userId);
        audit.setStudyId(ResearchAuthorityService.STUDY_ID);
        audit.setSchemaVersion(1);
        audit.setSampleCount(count);
        audit.setCreatedAt(Instant.now());
        audits.save(audit);
        return bytes.toByteArray();
    }

    public record Stats(String studyId, long sampleCount, long annotationCount,
                        long pendingReviewCount, long approvedCount) {}
}
