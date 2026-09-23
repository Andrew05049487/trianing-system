package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface ResearchAnnotationRepository extends JpaRepository<ResearchAnnotationEntity, String> {
    List<ResearchAnnotationEntity> findByTherapistUserId(Long therapistUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ResearchAnnotationEntity a where a.sampleId = :sampleId")
    Optional<ResearchAnnotationEntity> findForUpdate(@Param("sampleId") String sampleId);

    @Query("select a from ResearchAnnotationEntity a, ResearchSampleEntity s " +
        "where a.sampleId = s.id and a.status = 'SUBMITTED' and s.participantUserId in :patientIds")
    Page<ResearchAnnotationEntity> findSubmittedForPatients(
        @Param("patientIds") Collection<Long> patientIds, Pageable pageable);
}
