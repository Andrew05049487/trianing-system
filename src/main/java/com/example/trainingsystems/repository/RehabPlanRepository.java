package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.RehabPlanEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;

public interface RehabPlanRepository extends JpaRepository<RehabPlanEntity, Long> {
    @EntityGraph(attributePaths = "items")
    Optional<RehabPlanEntity> findByPatient_IdAndPlanDate(
        Long patientId,
        LocalDate planDate
    );

    @EntityGraph(attributePaths = "items")
    Optional<RehabPlanEntity> findByPatient_IdAndPlanId(
        Long patientId,
        String planId
    );

    Optional<RehabPlanEntity> findByPlanId(String planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("select plan from RehabPlanEntity plan " +
        "where plan.patient.id = :patientId and plan.planId = :planId")
    Optional<RehabPlanEntity> findForUpdate(
        @Param("patientId") Long patientId,
        @Param("planId") String planId
    );
}
