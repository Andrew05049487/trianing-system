package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long> {

    boolean existsByUserLowIdAndUserHighId(
        Long userLowId,
        Long userHighId
    );

    Optional<Friendship> findByUserLowIdAndUserHighId(
        Long userLowId,
        Long userHighId
    );

    List<Friendship> findByUserLowIdOrUserHighId(
        Long userLowId,
        Long userHighId
    );
}