package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchRetentionPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ResearchRetentionPolicyRepository extends JpaRepository<ResearchRetentionPolicyEntity, Long> {
    Optional<ResearchRetentionPolicyEntity> findTopByStudyIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
        String studyId, Instant now);
    List<ResearchRetentionPolicyEntity> findByStudyIdOrderByCreatedAtDesc(String studyId);
    boolean existsByStudyIdAndPolicyVersion(String studyId, String policyVersion);
}
