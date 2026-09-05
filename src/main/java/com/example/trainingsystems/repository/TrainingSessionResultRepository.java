package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.TrainingSessionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingSessionResultRepository
    extends JpaRepository<TrainingSessionResultEntity, Long> {

    Optional<TrainingSessionResultEntity> findBySessionId(String sessionId);

    List<TrainingSessionResultEntity>
        findAllByPatient_IdOrderByCompletedAtDesc(Long patientId);
}
