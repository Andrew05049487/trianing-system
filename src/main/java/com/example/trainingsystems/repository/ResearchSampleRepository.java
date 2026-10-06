package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchSampleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface ResearchSampleRepository extends JpaRepository<ResearchSampleEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResearchSampleEntity s where s.id=:id")
    Optional<ResearchSampleEntity> findForUpdate(@Param("id") String id);
    Optional<ResearchSampleEntity> findByParticipantUserIdAndClientSampleId(Long participantUserId, String clientSampleId);
    Optional<ResearchSampleEntity> findByParticipantUserIdAndAttemptId(Long participantUserId, String attemptId);
    Page<ResearchSampleEntity> findByParticipantUserId(Long participantUserId, Pageable pageable);
    Page<ResearchSampleEntity> findByParticipantUserIdIn(Collection<Long> participantUserIds, Pageable pageable);
    List<ResearchSampleEntity> findByParticipantUserId(Long participantUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ResearchSampleEntity s where s.expiresAt is not null and s.expiresAt <= :now order by s.expiresAt asc")
    List<ResearchSampleEntity> findExpiredForUpdate(@Param("now") Instant now, Pageable pageable);
}
