package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ExerciseAssignmentEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseAssignmentRepository
    extends JpaRepository<ExerciseAssignmentEntity, Long> {

    Optional<ExerciseAssignmentEntity> findByExercise_IdAndPatient_Id(
        Long exerciseId,
        Long patientId
    );

    List<ExerciseAssignmentEntity>
        findAllByPatient_IdAndAssignedByTherapist_IdAndActiveTrue(
            Long patientId,
            Long therapistId
        );

    List<ExerciseAssignmentEntity> findAllByPatient_IdAndActiveTrue(
        Long patientId,
        Sort sort
    );
}
