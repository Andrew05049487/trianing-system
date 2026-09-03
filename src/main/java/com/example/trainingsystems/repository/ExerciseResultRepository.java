package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ExerciseResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseResultRepository extends JpaRepository<ExerciseResult, Long> {
    List<ExerciseResult> findByUserId(Long userId);
}