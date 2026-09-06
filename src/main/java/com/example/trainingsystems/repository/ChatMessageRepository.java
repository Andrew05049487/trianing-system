package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessageEntity, Long> {

    @EntityGraph(attributePaths = {"conversation", "sender"})
    List<ChatMessageEntity> findTop200ByConversation_IdOrderBySentAtDesc(
        Long conversationId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ChatMessageEntity message
           set message.readAt = :readAt
         where message.conversation.id = :conversationId
           and message.sender.id <> :readerId
           and message.readAt is null
        """)
    int markUnreadMessagesAsRead(
        @Param("conversationId") Long conversationId,
        @Param("readerId") Long readerId,
        @Param("readAt") Instant readAt
    );

    @Query("""
        select message.conversation.id as conversationId,
               count(message.id) as unreadCount
          from ChatMessageEntity message
         where message.conversation.id in :conversationIds
           and message.sender.id <> :userId
           and message.readAt is null
         group by message.conversation.id
        """)
    List<UnreadCountView> countUnreadByConversationIds(
        @Param("conversationIds") Collection<Long> conversationIds,
        @Param("userId") Long userId
    );

    interface UnreadCountView {
        Long getConversationId();

        long getUnreadCount();
    }
}
