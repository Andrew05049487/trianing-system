package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository
        extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findBySenderIdAndReceiverId(
        Long senderId,
        Long receiverId
    );

    List<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(
        Long receiverId,
        String status
    );
}