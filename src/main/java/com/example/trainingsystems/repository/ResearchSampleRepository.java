package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchSampleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResearchSampleRepository extends JpaRepository<ResearchSampleEntity, String> {
    Optional<ResearchSampleEntity> findByParticipantUserIdAndClientSampleId(Long participantUserId, String clientSampleId);
    Page<ResearchSampleEntity> findByParticipantUserId(Long participantUserId, Pageable pageable);
    Page<ResearchSampleEntity> findByParticipantUserIdIn(Collection<Long> participantUserIds, Pageable pageable);
    List<ResearchSampleEntity> findByParticipantUserId(Long participantUserId);
}
