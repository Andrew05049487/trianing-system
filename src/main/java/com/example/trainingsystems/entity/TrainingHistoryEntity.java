package com.example.trainingsystems.entity;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

/**
 * 自由訓練（app「訓練進步曲線」）的歷史紀錄。
 *
 * (user_id, client_timestamp) 設 UNIQUE，
 * 讓同一筆重複上傳時只更新原資料，不產生重複列。
 */
@Entity
@Table(
    name = "training_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_training_history_user_ts",
        columnNames = {"user_id", "client_timestamp"}
    )
)
@Data
public class TrainingHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 同一次訓練的群組識別。
     * DB 欄位已由 SQL 新增為 NVARCHAR(100) NULL。
     */
    @Column(
        name = "session_id",
        columnDefinition = "NVARCHAR(100)"
    )
    private String sessionId;

    @Column(
        name = "action_name",
        nullable = false,
        columnDefinition = "NVARCHAR(100)"
    )
    private String actionName;

    @Column(name = "difficulty", nullable = false)
    private Integer difficulty;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "completed_reps", nullable = false)
    private Integer completedReps;

    @Column(name = "target_reps", nullable = false)
    private Integer targetReps;

    /** mistakeLogs 的筆數，另外存一份方便治療師端排序 / 統計。 */
    @Column(name = "mistake_count", nullable = false)
    private Integer mistakeCount;

    /** mistakeLogs 原始明細，以 JSON 陣列字串保存，不遺失細節。 */
    @Column(
        name = "mistake_logs",
        columnDefinition = "NVARCHAR(MAX)"
    )
    private String mistakeLogs;

    @Column(name = "body_score", precision = 5, scale = 2)
    private BigDecimal averageBodyScore;

    @Column(name = "body_rep_scores", columnDefinition = "NVARCHAR(MAX)")
    private String bodyRepScores;

    @Column(name = "template_score", precision = 5, scale = 2)
    private BigDecimal templateScore;

    @Column(name = "template_id", length = 160)
    private String templateId;

    @Column(name = "template_name", columnDefinition = "NVARCHAR(200)")
    private String templateName;

    @Column(name = "template_valid_rep_count", nullable = false)
    private Integer templateValidRepCount = 0;

    @Column(name = "template_rep_scores", columnDefinition = "NVARCHAR(MAX)")
    private String templateRepScores;

    @Column(
        name = "template_difference_summary",
        columnDefinition = "NVARCHAR(MAX)"
    )
    private String templateDifferenceSummary;

    /**
     * app 端 TrainingRecord.timestamp（yyyy-MM-dd HH:mm:ss），
     * 做為冪等上傳的鍵。
     */
    @Column(
        name = "client_timestamp",
        nullable = false,
        length = 32
    )
    private String clientTimestamp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
