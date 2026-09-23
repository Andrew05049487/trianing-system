package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchAnnotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchAnnotationRepository extends JpaRepository<ResearchAnnotationEntity, String> {
    List<ResearchAnnotationEntity> findByTherapistUserId(Long therapistUserId);
}
