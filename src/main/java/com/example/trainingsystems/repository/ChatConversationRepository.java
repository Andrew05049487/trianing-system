package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ChatConversationEntity;
import com.example.trainingsystems.entity.ChatConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository
        extends JpaRepository<ChatConversationEntity, Long> {

    Optional<ChatConversationEntity>
        findByParticipantOne_IdAndParticipantTwo_IdAndConversationType(
            Long participantOneId,
            Long participantTwoId,
            ChatConversationType conversationType
        );

    @EntityGraph(attributePaths = {"participantOne", "participantTwo"})
    @Query("""
        select conversation
        from ChatConversationEntity conversation
        where conversation.participantOne.id = :userId
           or conversation.participantTwo.id = :userId
        order by conversation.updatedAt desc
        """)
    List<ChatConversationEntity> findAllForUserOrderByUpdatedAtDesc(
        @Param("userId") Long userId
    );
}
