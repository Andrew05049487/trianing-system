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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Bounded, in-memory export; never writes training files to Render's disk. */
@Service
public class ResearchManagementService {
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
    @Value("${research.hand-consent-version:}")
    private String handConsentVersion = "";

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
        return exportApproved(userId, token, ResearchActionRegistry.STANDING);
    }

    @Transactional
    public byte[] exportApproved(Long userId, String token, String actionId) {
        return exportApproved(userId,token,actionId,1,null);
    }

    @Transactional
    public byte[] exportApproved(Long userId, String token, String actionId, int schemaVersion, String source) {
        authority.requireManager(authority.authenticated(userId, token));
        if (!java.util.Set.of(1,2,3).contains(schemaVersion)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"UNSUPPORTED_RESEARCH_SCHEMA");
        boolean bodyAttempt=schemaVersion==3;
        if (bodyAttempt && (!ResearchActionRegistry.STANDING.equals(actionId) ||
            source==null || !java.util.Set.of("phone","tv_pi").contains(source))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"BODY_EXPORT_SOURCE_REQUIRED");
        }
        var definition = bodyAttempt?ResearchActionRegistry.BODY_ATTEMPT:validator.action(actionId);
        if (definition == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "UNSUPPORTED_RESEARCH_ACTION");
        if (consentVersion.isBlank()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "RESEARCH_CONSENT_VERSION_UNSET");
        if (definition.schemaVersion() == 2 && (handConsentVersion.isBlank() || !handConsentVersion.equals(consentVersion))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HAND_RESEARCH_SCOPE_NOT_APPROVED");
        }
        var page = annotations.findByStatus("APPROVED", PageRequest.of(0, MAX_SAMPLES + 1));
        if (page.getTotalElements() > MAX_SAMPLES) throw new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "RESEARCH_EXPORT_BATCH_TOO_LARGE");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StringBuilder labels = new StringBuilder("sampleId,label,annotatorId,labelVersion,actionDefinitionVersion\n");
        Map<Long, String> aliases = new HashMap<>();
        Map<String,String> sessionAliases=new HashMap<>();
        var groups=mapper.createArrayNode();
        int count = 0;
        int uncompressed = 0;
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (ResearchAnnotationEntity annotation : page.getContent()) {
                if (annotation.getReviewerUserId() == null || annotation.getReviewedAt() == null ||
                    annotation.getReviewerUserId().equals(annotation.getTherapistUserId())) continue;
                ResearchSampleEntity sample = samples.findById(annotation.getSampleId()).orElse(null);
                if (sample == null) continue;
                if (!"ACTIVE".equals(sample.getDisposition())) continue;
                // Synthetic demos are never a formal training export, even on a non-demo server.
                if (String.valueOf(sample.getClientSampleId()).startsWith("DEMO-") ||
                    String.valueOf(sample.getSubjectId()).startsWith("DEV-SUBJECT-") ||
                    authority.isSyntheticParticipant(sample.getParticipantUserId())) continue;
                if (sample.getExpiresAt() == null || !sample.getExpiresAt().isAfter(Instant.now())) continue;
                ResearchConsentEntity consent = consents.findById(sample.getParticipantUserId()).orElse(null);
                if (consent == null || !consent.isActive() ||
                    !consentVersion.equals(consent.getConsentVersion()) ||
                    !sample.getSubjectId().equals(consent.getSubjectId())) continue;
                // Re-sanitize before export; never pass through unknown/direct identifiers.
                ObjectNode safe;
                try { safe = validator.validateStored(mapper.readTree(sample.getPayloadJson())).safePayload(); }
                catch (ResponseStatusException | JsonProcessingException invalid) { continue; }
                if (bodyAttempt && (!source.equals(safe.path("source").asText()) ||
                    safe.path("schemaVersion").asInt(-1)!=3)) continue;
                if (!definition.actionId().equals(safe.path("actionId").asText()) ||
                    !definition.acceptsVersion(safe) ||
                    !definition.version().equals(annotation.getActionDefinitionVersion()) ||
                    (definition.schemaVersion() == 2 && !"hand-research-v1".equals(annotation.getLabelVersion())) ||
                    (bodyAttempt && !"body-attempt-label-v1".equals(annotation.getLabelVersion())) ||
                    !definition.trainableLabels().contains(annotation.getLabel())) continue;
                safe.put("actionDefinitionVersion", definition.version());
                safe.put("sampleId", sample.getId());
                safe.put("subjectId", sample.getSubjectId());
                if (!trainingFeatures.matches(safe)) continue;
                if (bodyAttempt) {
                    String sessionGroup=sessionAliases.computeIfAbsent(sample.getSubjectId()+"|"+safe.path("sessionId").asText(),
                        ignored -> "session_"+(sessionAliases.size()+1));
                    String attemptGroup="attempt_"+(count+1);
                    safe.put("sessionId",sessionGroup);safe.put("attemptId",attemptGroup);
                    safe.remove("resampleOfSampleId");
                    var group=groups.addObject();
                    group.put("sampleId",sample.getId());group.put("subjectPseudonym",sample.getSubjectId());
                    group.put("sessionGrouping",sessionGroup);group.put("attemptGrouping",attemptGroup);
                    group.put("source",source);
                }
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
            manifest.put("schemaVersion", definition.schemaVersion());
            manifest.put("actionId", definition.actionId());
            manifest.put("actionDefinitionVersion", definition.version());
            if (bodyAttempt) {
                manifest.put("modality","body");manifest.put("source",source);
                manifest.put("extractorVersion",ResearchBodyAttemptValidator.EXTRACTOR);
                manifest.put("modelInputVersion",ResearchBodyAttemptValidator.INPUT);
                manifest.put("poseModelVersion","rtmpose-wholebody-133-v1");
                manifest.set("groups",groups);
                manifest.put("distributionPolicy","single-source-domain; no implicit phone/tv_pi pooling");
            }
            manifest.set("featureNames", mapper.valueToTree(definition.featureNames()));
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
        audit.setSchemaVersion(definition.schemaVersion());
        audit.setSampleCount(count);
        audit.setCreatedAt(Instant.now());
        audits.save(audit);
        return bytes.toByteArray();
    }

    public record Stats(String studyId, long sampleCount, long annotationCount,
                        long pendingReviewCount, long approvedCount) {}
}
