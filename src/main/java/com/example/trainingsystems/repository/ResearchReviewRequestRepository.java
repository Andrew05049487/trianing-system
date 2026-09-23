package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchReviewRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResearchReviewRequestRepository extends JpaRepository<ResearchReviewRequestEntity, Long> {
    Optional<ResearchReviewRequestEntity> findByUserIdAndStudyId(Long userId, String studyId);
    List<ResearchReviewRequestEntity> findByStudyIdAndStatusOrderByRequestedAtAsc(String studyId, String status);
    List<ResearchReviewRequestEntity> findByUserId(Long userId);
}
