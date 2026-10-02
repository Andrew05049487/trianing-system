package com.example.trainingsystems.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs inside account deletion, before users; keep unrelated users' records and research audits. */
@Service
public class AccountDataCleanupService {
    private final EntityManager entityManager;

    public AccountDataCleanupService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void deleteForAccount(Long userId) {
        entityManager.flush();
        delete("delete from ChatMessageEntity m where m.conversation.id in " +
            "(select c.id from ChatConversationEntity c where c.participantOne.id=:id or c.participantTwo.id=:id)", userId);
        delete("delete from ChatConversationEntity c where c.participantOne.id=:id or c.participantTwo.id=:id", userId);
        // Existing schema CASCADE removes only this patient's plan items / history video.
        delete("delete from RehabPlanEntity p where p.patient.id=:id", userId);
        delete("delete from TrainingHistoryEntity h where h.user.id=:id", userId);
        delete("delete from TrainingSessionResultEntity r where r.patient.id=:id", userId);
    }

    private void delete(String jpql, Long userId) {
        entityManager.createQuery(jpql).setParameter("id", userId).executeUpdate();
    }
}
