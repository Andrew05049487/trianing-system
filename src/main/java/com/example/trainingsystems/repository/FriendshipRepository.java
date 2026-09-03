package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long> {

    boolean existsByUserLowIdAndUserHighId(
        Long userLowId,
        Long userHighId
    );

    List<Friendship> findByUserLowIdOrUserHighId(
        Long userLowId,
        Long userHighId
    );
}