package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ResearchConsentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResearchConsentRepository extends JpaRepository<ResearchConsentEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ResearchConsentEntity c where c.userId = :id")
    java.util.Optional<ResearchConsentEntity> findForUpload(
        @org.springframework.data.repository.query.Param("id") Long id);
    List<ResearchConsentEntity> findByUserIdInAndActiveTrue(Collection<Long> userIds);
}
