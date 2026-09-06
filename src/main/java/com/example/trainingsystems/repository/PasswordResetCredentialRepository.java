package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.PasswordResetCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PasswordResetCredentialRepository
    extends JpaRepository<PasswordResetCredential, String> {

    List<PasswordResetCredential> findByUserIdAndConsumedAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCredential>
        findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId);
}
