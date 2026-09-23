package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchAnnotationRevisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchAnnotationRevisionRepository extends JpaRepository<ResearchAnnotationRevisionEntity, Long> {
    List<ResearchAnnotationRevisionEntity> findBySampleIdOrderByRevisionAsc(String sampleId);
    List<ResearchAnnotationRevisionEntity> findBySampleId(String sampleId);
}
