package com.example.trainingsystems.dto;

import lombok.Data;

import java.util.List;

/**
 * app 端上傳單筆自由訓練紀錄用的請求格式，
 * 欄位刻意對齊 Flutter 的 TrainingRecord，讓 app 幾乎原封不動就能送。
 */
@Data
public class TrainingHistoryRequest {

    private Long userId;

    private String actionName;

    private Integer difficulty;

    private Integer durationSeconds;

    private Integer completedReps;

    private Integer targetReps;

    private List<String> mistakeLogs;

    /** 對應 TrainingRecord.timestamp，做為冪等上傳的鍵。 */
    private String clientTimestamp;
}
