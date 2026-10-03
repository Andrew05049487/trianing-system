package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.ResearchGrantAuditEntity;
import com.example.trainingsystems.entity.ResearchGrantEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ResearchGrantAuditRepository;
import com.example.trainingsystems.repository.ResearchGrantRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Operator-only first-manager initialization. No public endpoint or base-role changes. */
@Service
public class ResearchManagerInitializationService {
    private final UserRepository users;
    private final ResearchGrantRepository grants;
    private final ResearchGrantAuditRepository audits;

    public ResearchManagerInitializationService(UserRepository users, ResearchGrantRepository grants,
                                                 ResearchGrantAuditRepository audits) {
        this.users = users;
        this.grants = grants;
        this.audits = audits;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void initialize(Long targetId, String expectedEmail, String authorizationReference) {
        if (targetId == null || targetId <= 0 || expectedEmail == null || expectedEmail.isBlank()
            || authorizationReference == null || !authorizationReference.matches("[A-Za-z0-9_.-]{1,32}")) {
            throw new IllegalStateException("Research initialization configuration invalid");
        }
        User target = users.findById(targetId)
            .orElseThrow(() -> new IllegalStateException("Research initialization target missing"));
        if (!expectedEmail.equalsIgnoreCase(target.getEmail()) || !"THERAPIST".equals(target.getRole())) {
            throw new IllegalStateException("Research initialization target identity mismatch");
        }
        ResearchGrantEntity grant = grants.findByUserIdAndStudyId(targetId, ResearchAuthorityService.STUDY_ID)
            .orElseGet(ResearchGrantEntity::new);
        if (grant.isCanManage()) return; // Restart-safe; never appends another audit/grant.
        if (grants.existsByStudyIdAndCanManageTrue(ResearchAuthorityService.STUDY_ID)) {
            throw new IllegalStateException("Research manager already exists; initialization refused");
        }
        grant.setUserId(targetId);
        grant.setStudyId(ResearchAuthorityService.STUDY_ID);
        grant.setCanManage(true);
        grant.setGrantedByUserId(targetId);
        grant.setUpdatedAt(Instant.now());
        grants.saveAndFlush(grant);
        ResearchGrantAuditEntity audit = new ResearchGrantAuditEntity();
        audit.setStudyId(ResearchAuthorityService.STUDY_ID);
        audit.setActorUserId(targetId);
        audit.setTargetUserId(targetId);
        audit.setAction(authorizationReference);
        audit.setCreatedAt(Instant.now());
        audits.saveAndFlush(audit);
    }
}
