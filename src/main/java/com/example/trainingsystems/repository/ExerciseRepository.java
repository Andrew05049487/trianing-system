package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
}