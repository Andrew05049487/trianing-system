package com.example.trainingsystems.dto;

import java.util.List;

import lombok.Data;

/**
 * App 上傳單筆自由訓練歷史紀錄。
 */
@Data
public class TrainingHistoryRequest {

    private Long userId;

    /**
     * 同一場訓練的識別碼。
     *
     * 例如同一次自動升級：
     *
     * Lv.1
     * Lv.2
     * Lv.3
     *
     * 三筆會使用同一個 sessionId。
     *
     * 舊 App 沒有這個欄位時允許 null。
     */
    private String sessionId;

    private String actionName;

    private Integer difficulty;

    private Integer durationSeconds;

    private Integer completedReps;

    private Integer targetReps;

    private List<String> mistakeLogs;

    /**
     * 對應 Flutter TrainingRecord.timestamp。
     *
     * 仍然保留為既有冪等上傳鍵。
     */
    private String clientTimestamp;
}