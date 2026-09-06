package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.UserAvatarEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAvatarRepository
        extends JpaRepository<UserAvatarEntity, Long> {
}
