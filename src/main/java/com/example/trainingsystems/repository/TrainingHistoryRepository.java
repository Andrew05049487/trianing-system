package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.TrainingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainingHistoryRepository
        extends JpaRepository<TrainingHistoryEntity, Long> {

    /** 讀取某使用者的全部自由訓練紀錄，新到舊。 */
    List<TrainingHistoryEntity> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** 用 (userId, clientTimestamp) 找既有紀錄，支援冪等上傳（upsert）。 */
    Optional<TrainingHistoryEntity> findByUser_IdAndClientTimestamp(
            Long userId, String clientTimestamp);
}