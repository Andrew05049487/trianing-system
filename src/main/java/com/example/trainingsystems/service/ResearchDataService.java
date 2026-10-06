package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import com.example.trainingsystems.entity.ResearchAnnotationRevisionEntity;
import com.example.trainingsystems.entity.ResearchAuditEntity;
import com.example.trainingsystems.entity.ResearchConsentEntity;
import com.example.trainingsystems.entity.ResearchSampleEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ResearchAnnotationRepository;
import com.example.trainingsystems.repository.ResearchAnnotationRevisionRepository;
import com.example.trainingsystems.repository.ResearchAuditRepository;
import com.example.trainingsystems.repository.ResearchConsentRepository;
import com.example.trainingsystems.repository.ResearchSampleRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ResearchDataService {
    private static final String PATIENT = "PATIENT";
    private static final String THERAPIST = "THERAPIST";

    private final UserRepository users;
    private final UserBindingRepository bindings;
    private final CustomExerciseIdentityService identity;
    private final ResearchConsentRepository consents;
    private final ResearchSampleRepository samples;
    private final ResearchAnnotationRepository annotations;
    private final ResearchAnnotationRevisionRepository revisions;
    private final ResearchAuditRepository audits;
    private final ResearchSampleValidator validator;
    private final ResearchAuthorityService authority;
    private final ResearchRetentionService retention;
    private final ObjectMapper mapper;
    private final boolean collectionEnabled;
    private final String currentConsentVersion;
    // Empty by default: existing standing consent never implicitly covers hand actions.
    @Value("${research.hand-consent-version:}")
    private String handConsentVersion = "";
    @org.springframework.beans.factory.annotation.Autowired
    private ResearchBodyAssignmentService bodyAssignments;

    public ResearchDataService(
        UserRepository users, UserBindingRepository bindings,
        CustomExerciseIdentityService identity, ResearchConsentRepository consents,
        ResearchSampleRepository samples, ResearchAnnotationRepository annotations,
        ResearchAnnotationRevisionRepository revisions,
        ResearchAuditRepository audits, ResearchSampleValidator validator,
        ResearchAuthorityService authority,
        ResearchRetentionService retention,
        ObjectMapper mapper,
        @Value("${research.collection-enabled:false}") boolean collectionEnabled,
        @Value("${research.consent-version:}") String currentConsentVersion
    ) {
        this.users = users;
        this.bindings = bindings;
        this.identity = identity;
        this.consents = consents;
        this.samples = samples;
        this.annotations = annotations;
        this.revisions = revisions;
        this.audits = audits;
        this.validator = validator;
        this.authority = authority;
        this.retention = retention;
        this.mapper = mapper;
        this.collectionEnabled = collectionEnabled;
        this.currentConsentVersion = currentConsentVersion;
    }

    @Transactional(readOnly = true)
    public ConsentView consent(Long userId, String token) {
        requireRole(userId, token, PATIENT);
        return consentView(consents.findById(userId).orElse(null));
    }

    @Transactional
    public ConsentView setConsent(Long userId, String token, boolean agree, String version) {
        requireRole(userId, token, PATIENT);
        ResearchConsentEntity consent = consents.findById(userId)
            .orElseGet(ResearchConsentEntity::new);
        if (agree) {
            requireCollectionEnabled();
            if (!currentConsentVersion.equals(version)) throw badRequest("CONSENT_VERSION_MISMATCH");
            if (consent.getSubjectId() == null) consent.setSubjectId(UUID.randomUUID().toString());
            consent.setConsentVersion(version);
            consent.setConsentedAt(Instant.now());
            consent.setWithdrawnAt(null);
            consent.setActive(true);
        } else {
            if (consent.getSubjectId() == null) return consentView(null);
            consent.setActive(false);
            consent.setWithdrawnAt(Instant.now());
        }
        consent.setUserId(userId);
        consents.save(consent);
        audit(userId, agree ? "CONSENT_GRANTED" : "CONSENT_WITHDRAWN", null);
        return consentView(consent);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public SampleView upload(Long userId, String token, JsonNode payload) {
        requireRole(userId, token, PATIENT);
        requireCollectionEnabled();
        // Serialize v3 retries before duplicate lookup using the existing consent row.
        boolean bodyAttempt = payload != null && payload.path("schemaVersion").asInt(-1)==3;
        ResearchConsentEntity consent = (bodyAttempt ? consents.findForUpload(userId) : consents.findById(userId))
            .orElseThrow(() -> forbidden("RESEARCH_CONSENT_REQUIRED"));
        if (!consent.isActive() || !currentConsentVersion.equals(consent.getConsentVersion())) {
            throw forbidden("RESEARCH_CONSENT_REQUIRED");
        }
        ResearchSampleValidator.ValidatedSample validated = validator.validate(payload);
        if (bodyAttempt) bodyAssignments.requireAssigned(userId, validated.safePayload());
        if (payload.path("schemaVersion").asInt(-1) == 2 &&
            (handConsentVersion.isBlank() || !handConsentVersion.equals(currentConsentVersion) ||
                !handConsentVersion.equals(consent.getConsentVersion()))) {
            throw forbidden("HAND_RESEARCH_SCOPE_NOT_APPROVED");
        }
        String canonical = writeJson(validated.safePayload());
        String fingerprint = sha256(canonical);
        Optional<ResearchSampleEntity> previous = samples
            .findByParticipantUserIdAndClientSampleId(userId, validated.clientId());
        if (bodyAttempt) {
            var sameAttempt = samples.findByParticipantUserIdAndAttemptId(userId,
                validated.safePayload().path("attemptId").asText());
            if (sameAttempt.isPresent()) {
                if (!fingerprint.equals(sameAttempt.get().getClientPayloadHash())) throw conflict("RESEARCH_ATTEMPT_ID_CONFLICT");
                return sampleView(sameAttempt.get());
            }
        }
        if (previous.isPresent()) {
            if (!fingerprint.equals(previous.get().getClientPayloadHash())) {
                throw conflict("RESEARCH_SAMPLE_ID_CONFLICT");
            }
            return sampleView(previous.get());
        }
        ResearchSampleEntity sample = new ResearchSampleEntity();
        sample.setId(UUID.randomUUID().toString());
        sample.setParticipantUserId(userId);
        sample.setSubjectId(consent.getSubjectId());
        sample.setClientSampleId(validated.clientId());
        sample.setClientPayloadHash(fingerprint);
        sample.setCapturedAt(validated.capturedAt());
        sample.setUploadedAt(Instant.now());
        var policy = retention.currentPolicy().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RESEARCH_RETENTION_UNSET"));
        sample.setRetentionPolicyVersion(policy.getPolicyVersion());
        sample.setExpiresAt(sample.getUploadedAt().plusSeconds(policy.getRetentionDays() * 86400L));
        sample.setMovementSide(validated.side());
        sample.setCameraView(validated.cameraView());
        String resampleOf = validated.safePayload().path("resampleOfSampleId").asText(null);
        if (resampleOf != null) {
            ResearchSampleEntity parent = findSample(resampleOf);
            if (!userId.equals(parent.getParticipantUserId()) ||
                !"NEEDS_RESAMPLE".equals(parent.getDisposition()) ||
                !Integer.valueOf(3).equals(parent.getSchemaVersion()) ||
                !validated.safePayload().path("exerciseId").asText().equals(parent.getExerciseId()) ||
                !validated.safePayload().path("exerciseType").asText().equals(parent.getExerciseType()) ||
                !consent.getSubjectId().equals(parent.getSubjectId()) ||
                parent.getExpiresAt()==null || !parent.getExpiresAt().isAfter(Instant.now())) {
                throw forbidden("INVALID_RESAMPLE_CONTEXT");
            }
            sample.setResampleOfSampleId(parent.getId());
        }
        if (bodyAttempt) {
            var v3=validated.safePayload();
            sample.setModality("body");sample.setSource(v3.path("source").asText());
            sample.setSchemaVersion(3);sample.setActionId(v3.path("actionId").asText());
            sample.setSessionId(v3.path("sessionId").asText());sample.setAttemptId(v3.path("attemptId").asText());
            sample.setExerciseType(v3.path("exerciseType").asText());sample.setExerciseId(v3.path("exerciseId").asText());
        }
        ObjectNode stored = validated.safePayload().deepCopy();
        stored.put("sampleId", sample.getId());
        stored.put("subjectId", consent.getSubjectId());
        sample.setPayloadJson(writeJson(stored));
        try {
            samples.saveAndFlush(sample);
        } catch (DataIntegrityViolationException error) {
            throw conflict("RESEARCH_SAMPLE_ID_CONFLICT");
        }
        audit(userId, "SAMPLE_UPLOADED", sample.getId());
        return sampleView(sample);
    }

    @Transactional(readOnly = true)
    public Page<SampleView> list(Long userId, String token, int page, int size) {
        User viewer = authenticated(userId, token);
        if (page < 0 || size < 1 || size > 50) throw badRequest("INVALID_PAGE");
        PageRequest paging = PageRequest.of(page, size, Sort.by("capturedAt").descending());
        if (hasRole(viewer, PATIENT)) {
            return samples.findByParticipantUserId(userId, paging).map(this::sampleView);
        }
        if (!hasRole(viewer, THERAPIST)) throw forbidden("RESEARCH_ROLE_DENIED");
        if (!authority.canAnnotate(viewer) && !authority.canReview(viewer)) {
            throw forbidden("RESEARCH_ACCESS_DENIED");
        }
        List<Long> patientIds = bindings
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(userId, THERAPIST)
            .stream().map(binding -> binding.getPatient().getId()).distinct().toList();
        if (patientIds.isEmpty()) return Page.empty(paging);
        List<Long> consentedIds = consents.findByUserIdInAndActiveTrue(patientIds)
            .stream().filter(c -> currentConsentVersion.equals(c.getConsentVersion()))
            .map(ResearchConsentEntity::getUserId).toList();
        if (consentedIds.isEmpty()) return Page.empty(paging);
        return samples.findByParticipantUserIdIn(consentedIds, paging).map(this::sampleView);
    }

    @Transactional(readOnly = true)
    public SampleDetail detail(Long userId, String token, String sampleId) {
        User viewer = authenticated(userId, token);
        ResearchSampleEntity sample = findSample(sampleId);
        requireAccess(viewer, sample);
        ResearchAnnotationEntity annotation = annotations.findById(sampleId).orElse(null);
        try {
            return new SampleDetail(sampleView(sample), mapper.readTree(sample.getPayloadJson()),
                annotation == null ? null : annotationView(annotation));
        } catch (JsonProcessingException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "RESEARCH_SAMPLE_CORRUPT");
        }
    }

    @Transactional
    public AnnotationView label(Long userId, String token, String sampleId,
                                String label, String note, String labelVersion,
                                String actionDefinitionVersion) {
        return label(userId,token,sampleId,label,note,labelVersion,actionDefinitionVersion,null);
    }

    @Transactional
    public AnnotationView label(Long userId, String token, String sampleId,
                                String label, String note, String labelVersion,
                                String actionDefinitionVersion, Integer expectedRevision) {
        User therapist = requireRole(userId, token, THERAPIST);
        authority.requireAnnotator(therapist);
        ResearchSampleEntity sample = findSample(sampleId);
        requireAccess(therapist, sample);
        sample = lockBodySample(sample);
        var definition = sampleDefinition(sample);
        if (definition == null || !definition.labels().contains(label) ||
            labelVersion == null || !labelVersion.matches("[A-Za-z0-9_.-]{1,64}") ||
            (definition.schemaVersion() == 2 && !"hand-research-v1".equals(labelVersion)) ||
            (definition.schemaVersion() == 3 && !"body-attempt-label-v1".equals(labelVersion)) ||
            actionDefinitionVersion == null ||
            !definition.version().equals(actionDefinitionVersion) ||
            (note != null && note.length() > 1000)) throw badRequest("INVALID_RESEARCH_LABEL");
        ResearchAnnotationEntity annotation = annotations.findForUpdate(sampleId)
            .orElseGet(ResearchAnnotationEntity::new);
        checkRevision(sample, annotation, expectedRevision);
        if (!"ACTIVE".equals(sample.getDisposition())) throw conflict("RESEARCH_SAMPLE_NOT_ACTIVE");
        if (annotation.getTherapistUserId() != null &&
            !annotation.getTherapistUserId().equals(userId)) {
            throw forbidden("LABEL_OWNED_BY_OTHER_THERAPIST");
        }
        if ("SUBMITTED".equals(annotation.getStatus()) ||
            "APPROVED".equals(annotation.getStatus())) throw conflict("RESEARCH_LABEL_LOCKED");
        annotation.setSampleId(sampleId);
        annotation.setSchemaVersion(sample.getSchemaVersion());
        annotation.setTherapistUserId(userId);
        annotation.setLabel(label);
        annotation.setNote(note == null ? "" : note.trim());
        annotation.setLabelVersion(labelVersion);
        annotation.setActionDefinitionVersion(actionDefinitionVersion);
        annotation.setUpdatedAt(Instant.now());
        annotation.setStatus("DRAFT");
        annotation.setRevision(annotation.getRevision() + 1);
        annotations.save(annotation);
        snapshot(annotation, userId);
        audit(userId, "LABEL_DRAFT_SAVED", sampleId);
        return annotationView(annotation);
    }

    @Transactional
    public AnnotationView submitLabel(Long userId, String token, String sampleId) {
        return submitLabel(userId,token,sampleId,null);
    }

    @Transactional
    public AnnotationView submitLabel(Long userId, String token, String sampleId, Integer expectedRevision) {
        User annotator = requireRole(userId, token, THERAPIST);
        authority.requireAnnotator(annotator);
        ResearchSampleEntity sample = findSample(sampleId);
        requireAccess(annotator, sample);
        sample = lockBodySample(sample);
        ResearchAnnotationEntity annotation = annotations.findForUpdate(sampleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_LABEL_NOT_FOUND"));
        checkRevision(sample, annotation, expectedRevision);
        if (!"ACTIVE".equals(sample.getDisposition())) throw conflict("RESEARCH_SAMPLE_NOT_ACTIVE");
        if (!userId.equals(annotation.getTherapistUserId())) throw forbidden("LABEL_OWNED_BY_OTHER_THERAPIST");
        if (!"DRAFT".equals(annotation.getStatus()) && !"RETURNED".equals(annotation.getStatus()) &&
            !"LABELED".equals(annotation.getStatus())) throw conflict("RESEARCH_LABEL_NOT_EDITABLE");
        annotation.setStatus("SUBMITTED");
        annotation.setSubmittedAt(Instant.now());
        annotation.setReviewNote(null);
        annotation.setReviewerUserId(null);
        annotation.setReviewedAt(null);
        annotation.setRevision(annotation.getRevision() + 1);
        annotation.setUpdatedAt(Instant.now());
        annotations.save(annotation);
        snapshot(annotation, userId);
        audit(userId, "LABEL_SUBMITTED", sampleId);
        return annotationView(annotation);
    }

    @Transactional(readOnly = true)
    public Page<SampleView> reviewQueue(Long userId, String token, int page, int size) {
        User reviewer = requireRole(userId, token, THERAPIST);
        authority.requireReviewer(reviewer);
        if (page < 0 || size < 1 || size > 50) throw badRequest("INVALID_PAGE");
        List<Long> patientIds = bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(userId, THERAPIST)
            .stream().map(binding -> binding.getPatient().getId()).distinct().toList();
        if (patientIds.isEmpty()) return Page.empty(PageRequest.of(page, size));
        List<Long> consentedIds = consents.findByUserIdInAndActiveTrue(patientIds)
            .stream().filter(c -> currentConsentVersion.equals(c.getConsentVersion()))
            .map(ResearchConsentEntity::getUserId).toList();
        if (consentedIds.isEmpty()) return Page.empty(PageRequest.of(page, size));
        return annotations.findSubmittedForPatients(consentedIds, PageRequest.of(page, size))
            .map(a -> sampleView(findSample(a.getSampleId())));
    }

    @Transactional
    public AnnotationView reviewLabel(Long userId, String token, String sampleId,
                                      boolean approve, String reviewNote) {
        return reviewLabel(userId,token,sampleId,approve?"APPROVE":"RETURN",reviewNote,null,null);
    }

    @Transactional
    public AnnotationView reviewLabel(Long userId, String token, String sampleId,
                                      String decision, String reviewNote,
                                      String reasonCode, Integer expectedRevision) {
        User reviewer = requireRole(userId, token, THERAPIST);
        authority.requireReviewer(reviewer);
        ResearchSampleEntity sample = findSample(sampleId);
        requireAccess(reviewer, sample);
        sample = lockBodySample(sample);
        ResearchAnnotationEntity annotation = annotations.findForUpdate(sampleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_LABEL_NOT_FOUND"));
        if (!"SUBMITTED".equals(annotation.getStatus())) throw conflict("RESEARCH_LABEL_NOT_SUBMITTED");
        if (userId.equals(annotation.getTherapistUserId())) throw forbidden("RESEARCH_SELF_REVIEW_DENIED");
        checkRevision(sample, annotation, expectedRevision);
        if (decision==null || !Set.of("APPROVE","RETURN","REJECT","NEEDS_RESAMPLE").contains(decision)) throw badRequest("INVALID_REVIEW_DECISION");
        boolean approve = "APPROVE".equals(decision);
        if (!approve && reasonCode != null &&
            !Set.of("TRACKING_LOST","LOW_QUALITY","INCOMPLETE_MOTION","WRONG_ACTION","OTHER").contains(reasonCode)) throw badRequest("INVALID_REVIEW_REASON");
        if (Set.of("REJECT","NEEDS_RESAMPLE").contains(decision) && reasonCode==null) throw badRequest("REVIEW_REASON_REQUIRED");
        if (reviewNote != null && reviewNote.length() > 1000) throw badRequest("INVALID_REVIEW_NOTE");
        if (!approve && (reviewNote == null || reviewNote.isBlank())) throw badRequest("REVIEW_REASON_REQUIRED");
        annotation.setStatus(approve ? "APPROVED" : "RETURNED");
        annotation.setReviewerUserId(userId);
        annotation.setReviewedAt(Instant.now());
        annotation.setReviewNote(reviewNote == null ? "" : reviewNote.trim());
        annotation.setReasonCode(approve?null:(reasonCode==null?"OTHER":reasonCode));
        sample.setDisposition(switch(decision) {
            case "REJECT" -> "REJECTED";
            case "NEEDS_RESAMPLE" -> "NEEDS_RESAMPLE";
            default -> "ACTIVE";
        });
        samples.save(sample);
        annotation.setUpdatedAt(Instant.now());
        annotation.setRevision(annotation.getRevision() + 1);
        annotations.save(annotation);
        snapshot(annotation, userId);
        audit(userId, "LABEL_" + (approve?"APPROVED":decision), sampleId);
        return annotationView(annotation);
    }

    @Transactional
    public void deleteOwnSample(Long userId, String token, String sampleId) {
        requireRole(userId, token, PATIENT);
        ResearchSampleEntity sample = findSample(sampleId);
        if (!sample.getParticipantUserId().equals(userId)) throw forbidden("RESEARCH_ACCESS_DENIED");
        annotations.deleteById(sampleId);
        revisions.deleteAll(revisions.findBySampleId(sampleId));
        samples.delete(sample);
        retention.recordDeletion(sample, "PATIENT_DELETED", userId);
        audit(userId, "SAMPLE_DELETED", sampleId);
    }

    @Transactional
    public void deleteMyData(Long userId, String token) {
        requireRole(userId, token, PATIENT);
        List<ResearchSampleEntity> owned = samples.findByParticipantUserId(userId);
        for (ResearchSampleEntity sample : owned) {
            annotations.findById(sample.getId()).ifPresent(annotations::delete);
            revisions.deleteAll(revisions.findBySampleId(sample.getId()));
            retention.recordDeletion(sample, "PATIENT_DELETED", userId);
        }
        samples.deleteAll(owned);
        consents.findById(userId).ifPresent(consents::delete);
        audit(userId, "DATA_DELETED", null);
    }

    private ConsentView consentView(ResearchConsentEntity consent) {
        String unavailableReason = collectionUnavailableReason();
        return new ConsentView(consent != null && consent.isActive(),
            consent == null ? null : consent.getSubjectId(),
            consent == null ? null : consent.getConsentVersion(),
            currentConsentVersion, unavailableReason == null, unavailableReason,
            unavailableReason == null && !handConsentVersion.isBlank() && handConsentVersion.equals(currentConsentVersion));
    }

    private String collectionUnavailableReason() {
        if (!collectionEnabled) return "RESEARCH_COLLECTION_NOT_ENABLED";
        if (currentConsentVersion.isBlank()) return "RESEARCH_CONSENT_VERSION_UNSET";
        if (retention.currentPolicy().isEmpty()) return "RESEARCH_RETENTION_UNSET";
        return null;
    }

    private User authenticated(Long id, String token) {
        if (id == null || token == null || token.isBlank()) throw unauthorized();
        User user = users.findById(id).orElseThrow(this::unauthorized);
        if (!identity.isConfigured() || !identity.isValid(user, token)) throw unauthorized();
        return user;
    }

    private User requireRole(Long id, String token, String role) {
        User user = authenticated(id, token);
        if (!hasRole(user, role)) throw forbidden("RESEARCH_ROLE_DENIED");
        return user;
    }

    private void requireAccess(User viewer, ResearchSampleEntity sample) {
        if (hasRole(viewer, PATIENT) && viewer.getId().equals(sample.getParticipantUserId())) return;
        if (hasRole(viewer, THERAPIST) &&
            (authority.canAnnotate(viewer) || authority.canReview(viewer)) &&
            consents.findById(sample.getParticipantUserId())
            .map(c -> c.isActive() && currentConsentVersion.equals(c.getConsentVersion()))
            .orElse(false) && bindings
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                sample.getParticipantUserId(), viewer.getId(), THERAPIST)) return;
        throw forbidden("RESEARCH_ACCESS_DENIED");
    }

    private void requireCollectionEnabled() {
        String reason = collectionUnavailableReason();
        if (reason != null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                reason);
        }
    }

    private ResearchSampleEntity lockBodySample(ResearchSampleEntity sample) {
        // A sample row exists even for the first draft: serialize before inserting
        // a missing annotation so concurrent editors get revision 409, not PK 500.
        return Integer.valueOf(3).equals(sample.getSchemaVersion())
            ? samples.findForUpdate(sample.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_SAMPLE_NOT_FOUND"))
            : sample;
    }

    private ResearchSampleEntity findSample(String id) {
        return samples.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_SAMPLE_NOT_FOUND"));
    }

    private SampleView sampleView(ResearchSampleEntity sample) {
        ResearchAnnotationEntity annotation = annotations.findById(sample.getId()).orElse(null);
        var definition = sampleDefinition(sample);
        return new SampleView(sample.getId(), sample.getClientSampleId(), sample.getSubjectId(),
            sample.getMovementSide(), sample.getCameraView(), sample.getCapturedAt(),
            annotation == null ? "UNLABELED" : annotation.getStatus(),
            definition == null ? null : definition.actionId(),
            definition == null ? null : definition.schemaVersion(),
            definition == null ? null : definition.version(),
            sample.getModality(),sample.getSource(),sample.getSessionId(),sample.getAttemptId(),
            sample.getExerciseType(),sample.getExerciseId(),sample.getDisposition(),
            sample.getResampleOfSampleId(),
            annotation==null?null:annotation.getReasonCode());
    }

    private ResearchActionRegistry.Definition sampleDefinition(ResearchSampleEntity sample) {
        if (sample.getPayloadJson() == null) return null;
        try { return validator.definition(mapper.readTree(sample.getPayloadJson())); }
        catch (JsonProcessingException error) { return null; }
    }

    private AnnotationView annotationView(ResearchAnnotationEntity annotation) {
        return new AnnotationView(annotation.getLabel(), annotation.getNote(),
            annotation.getLabelVersion(), annotation.getActionDefinitionVersion(),
            annotation.getStatus(), annotation.getUpdatedAt(), annotation.getRevision(),
            annotation.getTherapistUserId(), annotation.getSubmittedAt(),
            annotation.getReviewerUserId(), annotation.getReviewedAt(), annotation.getReviewNote(),
            annotation.getReasonCode());
    }

    private void snapshot(ResearchAnnotationEntity annotation, Long actorId) {
        ResearchAnnotationRevisionEntity revision = new ResearchAnnotationRevisionEntity();
        revision.setSampleId(annotation.getSampleId());
        revision.setSchemaVersion(annotation.getSchemaVersion());
        revision.setRevision(annotation.getRevision());
        revision.setActorUserId(actorId);
        revision.setAnnotatorUserId(annotation.getTherapistUserId());
        revision.setLabel(annotation.getLabel());
        revision.setNote(annotation.getNote());
        revision.setLabelVersion(annotation.getLabelVersion());
        revision.setActionDefinitionVersion(annotation.getActionDefinitionVersion());
        revision.setStatus(annotation.getStatus());
        revision.setReviewerUserId(annotation.getReviewerUserId());
        revision.setReviewNote(annotation.getReviewNote());
        revision.setReasonCode(annotation.getReasonCode());
        revision.setDisposition(samples.findById(annotation.getSampleId())
            .map(ResearchSampleEntity::getDisposition).orElse(null));
        revision.setCreatedAt(Instant.now());
        revisions.save(revision);
    }

    private void audit(Long actorId, String action, String sampleId) {
        ResearchAuditEntity event = new ResearchAuditEntity();
        event.setActorUserId(actorId);
        event.setAction(action);
        event.setSampleId(sampleId);
        if (sampleId != null) {
            event.setSchemaVersion(samples.findById(sampleId)
                .map(ResearchSampleEntity::getSchemaVersion).orElse(null));
        }
        event.setCreatedAt(Instant.now());
        audits.save(event);
    }

    private String writeJson(JsonNode node) {
        try { return mapper.writeValueAsString(node); }
        catch (JsonProcessingException error) { throw badRequest("INVALID_RESEARCH_SAMPLE"); }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static boolean hasRole(User user, String role) {
        return role.equalsIgnoreCase(user.getRole());
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RESEARCH_AUTH_REQUIRED");
    }

    private static ResponseStatusException forbidden(String code) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, code);
    }

    private static ResponseStatusException badRequest(String code) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }

    private static ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }

    private static void checkRevision(ResearchSampleEntity sample, ResearchAnnotationEntity annotation, Integer expected) {
        if ((Integer.valueOf(3).equals(sample.getSchemaVersion()) && expected==null) ||
            (expected!=null && expected!=annotation.getRevision())) throw conflict("RESEARCH_STALE_REVISION");
    }

    public record ConsentView(boolean active, String subjectId, String consentVersion,
                              String currentVersion, boolean available, String unavailableReason, boolean handAvailable) {}
    public record SampleView(String id, String clientSampleId, String subjectId,
                             String movementSide, String cameraView, Instant capturedAt,
                             String annotationStatus, String actionId, Integer schemaVersion,
                             String actionDefinitionVersion, String modality, String source,
                             String sessionId, String attemptId, String exerciseType,
                             String exerciseId, String disposition, String resampleOfSampleId,
                             String reasonCode) {}
    public record AnnotationView(String label, String note, String labelVersion,
                                 String actionDefinitionVersion, String status, Instant updatedAt,
                                 int revision, Long annotatorUserId, Instant submittedAt,
                                 Long reviewerUserId, Instant reviewedAt, String reviewNote, String reasonCode) {}
    public record SampleDetail(SampleView sample, JsonNode payload, AnnotationView annotation) {}
}
