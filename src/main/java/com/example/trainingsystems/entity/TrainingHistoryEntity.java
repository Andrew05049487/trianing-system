package com.example.trainingsystems.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自由訓練（app「訓練進步曲線」）的歷史紀錄。
 *
 * 這張表是為了收 app 端 TrainingRecord 的原始形狀而新增的，
 * 跟舊的 exercise_result（需要 exercise FK）以及新的
 * training_session_result（只收已指派、整組完成的動作）都不同，
 * 這裡刻意寬鬆：不需要 exercise FK、也不限定組數，
 * 病患在樹莓派離線環境練完、回到有網路時再指定上傳哪幾筆。
 *
 * (user_id, client_timestamp) 設 UNIQUE，讓「同一筆重複上傳」是冪等的，
 * 不會在資料庫裡產生重複列。
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
    @Column(name = "mistake_logs", columnDefinition = "NVARCHAR(MAX)")
    private String mistakeLogs;

    /** app 端 TrainingRecord.timestamp（"yyyy-MM-dd HH:mm:ss"），做為冪等上傳的鍵。 */
    @Column(name = "client_timestamp", nullable = false, length = 32)
    private String clientTimestamp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
