package com.example.trainingsystems.repository;

import com.example.trainingsystems.entity.TrainingHistoryVideoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TrainingHistoryVideoRepository
        extends JpaRepository<TrainingHistoryVideoEntity, Long> {

    /** 只查主鍵，不把大型 video_data 載入歷史列表。 */
    @Query("select video.historyId from TrainingHistoryVideoEntity video " +
        "where video.historyId in :historyIds")
    List<Long> findExistingHistoryIds(
        @Param("historyIds") Collection<Long> historyIds
    );
}
