package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomRehabExerciseRepository
    extends JpaRepository<CustomRehabExerciseEntity, String> {

    Optional<CustomRehabExerciseEntity> findByIdAndCreatedByTherapist_Id(
        String id,
        Long therapistId
    );

    List<CustomRehabExerciseEntity> findAllByCreatedByTherapist_Id(
        Long therapistId,
        Sort sort
    );
}
