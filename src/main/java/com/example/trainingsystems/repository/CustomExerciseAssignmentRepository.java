package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.CustomExerciseAssignmentEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomExerciseAssignmentRepository
    extends JpaRepository<CustomExerciseAssignmentEntity, Long> {

    Optional<CustomExerciseAssignmentEntity> findByCustomExercise_IdAndPatient_Id(
        String exerciseId,
        Long patientId
    );

    Optional<CustomExerciseAssignmentEntity>
        findByCustomExercise_IdAndPatient_IdAndActiveTrue(
            String exerciseId,
            Long patientId
        );

    List<CustomExerciseAssignmentEntity>
        findAllByCustomExercise_IdAndAssignedByTherapist_IdAndActiveTrue(
            String exerciseId,
            Long therapistId,
            Sort sort
        );

    List<CustomExerciseAssignmentEntity> findAllByPatient_IdAndActiveTrue(
        Long patientId,
        Sort sort
    );

    void deleteAllByCustomExercise_Id(String exerciseId);
}
