package com.example.trainingsystems.dto;

import java.util.List;
import java.math.BigDecimal;

import lombok.Data;

/**
 * app 端上傳單筆自由訓練紀錄用的請求格式，
 * 欄位刻意對齊 Flutter 的 TrainingRecord。
 */
@Data
public class TrainingHistoryRequest {

    private Long userId;

    /**
     * 同一次訓練的群組識別：
     * auto:xxxx   = 自動升級，同一場各難度共用
     * manual:xxxx = 手動升級／獨立紀錄
     */
    private String sessionId;

    private String actionName;

    private Integer difficulty;

    private Integer durationSeconds;

    private Integer completedReps;

    private Integer targetReps;

    private List<String> mistakeLogs;

    private BigDecimal averageBodyScore;

    private List<Integer> bodyRepScores;

    private BigDecimal templateScore;

    private String templateId;

    private String templateName;

    private Integer templateValidRepCount;

    private List<BigDecimal> templateRepScores;

    private List<String> templateDifferenceSummary;

    /** 對應 TrainingRecord.timestamp，做為冪等上傳的鍵。 */
    private String clientTimestamp;
}
