package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResearchConsentRepository extends JpaRepository<ResearchConsentEntity, Long> {
    List<ResearchConsentEntity> findByUserIdInAndActiveTrue(Collection<Long> userIds);
}
