package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResearchGrantRepository extends JpaRepository<ResearchGrantEntity, Long> {
    Optional<ResearchGrantEntity> findByUserIdAndStudyId(Long userId, String studyId);
    List<ResearchGrantEntity> findByStudyId(String studyId);
    boolean existsByStudyIdAndCanManageTrue(String studyId);
    long countByStudyIdAndCanManageTrue(String studyId);
    List<ResearchGrantEntity> findByUserId(Long userId);
}
