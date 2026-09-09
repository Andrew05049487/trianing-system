package com.example.trainingsystems.entity;

import java.time.LocalDateTime;

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

@Entity
@Table(
    name = "training_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_training_history_user_ts",
        columnNames = {
            "user_id",
            "client_timestamp"
        }
    )
)
@Data
public class TrainingHistoryEntity {

    @Id
    @GeneratedValue(
        strategy = GenerationType.IDENTITY
    )
    private Long id;

    /**
     * 同一場訓練識別碼。
     *
     * Lv.1 / Lv.2 / Lv.3 可以各自一列，
     * 但 session_id 相同。
     */
    @Column(
        name = "session_id",
        columnDefinition = "NVARCHAR(100)"
    )
    private String sessionId;

    @ManyToOne
    @JoinColumn(
        name = "user_id",
        nullable = false
    )
    private User user;

    @Column(
        name = "action_name",
        nullable = false,
        columnDefinition = "NVARCHAR(100)"
    )
    private String actionName;

    @Column(
        name = "difficulty",
        nullable = false
    )
    private Integer difficulty;

    @Column(
        name = "duration_seconds",
        nullable = false
    )
    private Integer durationSeconds;

    @Column(
        name = "completed_reps",
        nullable = false
    )
    private Integer completedReps;

    @Column(
        name = "target_reps",
        nullable = false
    )
    private Integer targetReps;

    @Column(
        name = "mistake_count",
        nullable = false
    )
    private Integer mistakeCount;

    @Column(
        name = "mistake_logs",
        columnDefinition = "NVARCHAR(MAX)"
    )
    private String mistakeLogs;

    @Column(
        name = "client_timestamp",
        nullable = false,
        length = 32
    )
    private String clientTimestamp;

    @Column(
        name = "created_at",
        nullable = false
    )
    private LocalDateTime createdAt;
}