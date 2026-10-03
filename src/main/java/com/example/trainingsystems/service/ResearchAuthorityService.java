package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchGrantAuditEntity;
import com.example.trainingsystems.entity.ResearchGrantEntity;
import com.example.trainingsystems.entity.ResearchReviewRequestEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ResearchGrantAuditRepository;
import com.example.trainingsystems.repository.ResearchGrantRepository;
import com.example.trainingsystems.repository.ResearchReviewRequestRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Single-study additive authority. Flutter visibility is never an authorization decision. */
@Service
public class ResearchAuthorityService {
    public static final String STUDY_ID = "standing-knee-raise-v1";
    private final UserRepository users;
    private final CustomExerciseIdentityService identity;
    private final UserBindingRepository bindings;
    private final ResearchGrantRepository grants;
    private final ResearchReviewRequestRepository requests;
    private final ResearchGrantAuditRepository audits;

    public ResearchAuthorityService(UserRepository users, CustomExerciseIdentityService identity,
        UserBindingRepository bindings, ResearchGrantRepository grants,
        ResearchReviewRequestRepository requests, ResearchGrantAuditRepository audits) {
        this.users = users;
        this.identity = identity;
        this.bindings = bindings;
        this.grants = grants;
        this.requests = requests;
        this.audits = audits;
    }

    public User authenticated(Long id, String token) {
        if (id == null || token == null || token.isBlank()) throw unauthorized();
        User user = users.findById(id).orElseThrow(this::unauthorized);
        if (!identity.isConfigured() || !identity.isValid(user, token)) throw unauthorized();
        return user;
    }

    public boolean canAnnotate(User user) {
        return hasRole(user, "THERAPIST") && grant(user).map(ResearchGrantEntity::isCanAnnotate).orElse(false);
    }

    public boolean canReview(User user) {
        return hasRole(user, "THERAPIST") && grant(user).map(ResearchGrantEntity::isCanReview).orElse(false);
    }

    public boolean canManage(User user) {
        return grant(user).map(ResearchGrantEntity::isCanManage).orElse(false);
    }

    /** Dataset hygiene only: does not affect login, consent, upload, viewing or grants. */
    public boolean isSyntheticParticipant(Long userId) {
        if (userId == null) return false;
        return users.findById(userId).map(user -> user.getName() != null
            && user.getName().startsWith("DEMO ") && user.getEmail() != null
            && user.getEmail().toLowerCase(java.util.Locale.ROOT).endsWith("@demo.invalid"))
            .orElse(false);
    }

    public void requireAnnotator(User user) {
        if (!canAnnotate(user)) throw forbidden("RESEARCH_ANNOTATION_DENIED");
    }

    public void requireReviewer(User user) {
        if (!canReview(user)) throw forbidden("RESEARCH_REVIEW_DENIED");
    }

    public void requireManager(User user) {
        if (!canManage(user)) throw forbidden("RESEARCH_MANAGEMENT_DENIED");
    }

    @Transactional(readOnly = true)
    public AuthorityView me(Long id, String token) {
        User user = authenticated(id, token);
        String requestStatus = requests.findByUserIdAndStudyId(id, STUDY_ID)
            .map(ResearchReviewRequestEntity::getStatus).orElse("NONE");
        return new AuthorityView(STUDY_ID, canAnnotate(user), canReview(user),
            canManage(user), requestStatus);
    }

    @Transactional
    public ReviewRequestView requestReview(Long id, String token) {
        User therapist = authenticated(id, token);
        if (!hasRole(therapist, "THERAPIST") ||
            bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(id, "THERAPIST").isEmpty()) {
            throw forbidden("RESEARCH_REVIEW_REQUEST_DENIED");
        }
        if (!canAnnotate(therapist)) throw forbidden("RESEARCH_STUDY_GRANT_REQUIRED");
        if (canReview(therapist)) throw conflict("RESEARCH_REVIEW_ALREADY_GRANTED");
        ResearchReviewRequestEntity request = requests.findByUserIdAndStudyId(id, STUDY_ID)
            .orElseGet(ResearchReviewRequestEntity::new);
        if (!"PENDING".equals(request.getStatus())) {
            request.setRequestedAt(Instant.now());
            request.setStatus("PENDING");
            request.setDecidedAt(null);
            request.setDecidedByUserId(null);
        }
        request.setUserId(id);
        request.setStudyId(STUDY_ID);
        requests.save(request);
        return requestView(request);
    }

    @Transactional(readOnly = true)
    public List<ReviewRequestView> pendingRequests(Long id, String token) {
        requireManager(authenticated(id, token));
        return requests.findByStudyIdAndStatusOrderByRequestedAtAsc(STUDY_ID, "PENDING")
            .stream().map(this::requestView).toList();
    }

    @Transactional
    public ReviewRequestView decide(Long actorId, String token, Long requestId, boolean approve) {
        User manager = authenticated(actorId, token);
        requireManager(manager);
        ResearchReviewRequestEntity request = requests.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_REQUEST_NOT_FOUND"));
        if (!STUDY_ID.equals(request.getStudyId()) || !"PENDING".equals(request.getStatus())) {
            throw conflict("RESEARCH_REQUEST_NOT_PENDING");
        }
        if (actorId.equals(request.getUserId())) throw forbidden("RESEARCH_SELF_APPROVAL_DENIED");
        User target = users.findById(request.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_USER_NOT_FOUND"));
        if (!hasRole(target, "THERAPIST")) throw forbidden("RESEARCH_REVIEW_TARGET_NOT_THERAPIST");
        if (bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(target.getId(), "THERAPIST").isEmpty()) {
            throw forbidden("RESEARCH_REVIEW_TARGET_UNBOUND");
        }
        request.setStatus(approve ? "APPROVED" : "REJECTED");
        request.setDecidedAt(Instant.now());
        request.setDecidedByUserId(actorId);
        if (approve) {
            ResearchGrantEntity grant = grants.findByUserIdAndStudyId(target.getId(), STUDY_ID)
                .orElseGet(ResearchGrantEntity::new);
            grant.setUserId(target.getId());
            grant.setStudyId(STUDY_ID);
            grant.setCanReview(true);
            grant.setGrantedByUserId(actorId);
            grant.setUpdatedAt(Instant.now());
            grants.save(grant);
        }
        requests.save(request);
        audit(actorId, target.getId(), approve ? "REVIEW_APPROVED" : "REVIEW_REJECTED");
        return requestView(request);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AuthorityView setGrant(Long actorId, String token, Long targetId,
                                  boolean annotate, boolean review, boolean manage) {
        User manager = authenticated(actorId, token);
        requireManager(manager);
        User target = users.findById(targetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESEARCH_USER_NOT_FOUND"));
        if ((annotate || review) && !hasRole(target, "THERAPIST")) {
            throw forbidden("RESEARCH_TARGET_NOT_THERAPIST");
        }
        if ((annotate || review) &&
            bindings.findAllByLinkedUser_IdAndRelationshipIgnoreCase(targetId, "THERAPIST").isEmpty()) {
            throw forbidden("RESEARCH_TARGET_UNBOUND");
        }
        ResearchGrantEntity grant = grants.findByUserIdAndStudyId(targetId, STUDY_ID)
            .orElseGet(ResearchGrantEntity::new);
        if (grant.isCanManage() && !manage &&
            grants.countByStudyIdAndCanManageTrue(STUDY_ID) <= 1) {
            throw conflict("RESEARCH_LAST_MANAGER");
        }
        grant.setUserId(targetId);
        grant.setStudyId(STUDY_ID);
        grant.setCanAnnotate(annotate);
        grant.setCanReview(review);
        grant.setCanManage(manage);
        grant.setGrantedByUserId(actorId);
        grant.setUpdatedAt(Instant.now());
        grants.save(grant);
        audit(actorId, targetId, "GRANT_UPDATED");
        return new AuthorityView(STUDY_ID, annotate, review, manage,
            requests.findByUserIdAndStudyId(targetId, STUDY_ID)
                .map(ResearchReviewRequestEntity::getStatus).orElse("NONE"));
    }

    private java.util.Optional<ResearchGrantEntity> grant(User user) {
        if (user == null || user.getId() == null) return java.util.Optional.empty();
        return grants.findByUserIdAndStudyId(user.getId(), STUDY_ID);
    }

    private ReviewRequestView requestView(ResearchReviewRequestEntity request) {
        return new ReviewRequestView(request.getId(), request.getUserId(), request.getStatus(),
            request.getRequestedAt(), request.getDecidedAt());
    }

    private void audit(Long actor, Long target, String action) {
        ResearchGrantAuditEntity record = new ResearchGrantAuditEntity();
        record.setStudyId(STUDY_ID);
        record.setActorUserId(actor);
        record.setTargetUserId(target);
        record.setAction(action);
        record.setCreatedAt(Instant.now());
        audits.save(record);
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

    private static ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }

    public record AuthorityView(String studyId, boolean canAnnotate, boolean canReview,
                                boolean canManage, String reviewRequestStatus) {}
    public record ReviewRequestView(Long id, Long userId, String status,
                                    Instant requestedAt, Instant decidedAt) {}
}
