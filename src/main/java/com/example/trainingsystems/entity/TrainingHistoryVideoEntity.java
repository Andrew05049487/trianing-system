package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/** 影片獨立於歷史 metadata，避免列表查詢讀取 VARBINARY(MAX)。 */
@Entity
@Table(name = "training_history_video")
@Data
public class TrainingHistoryVideoEntity {
    @Id
    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @Column(name = "file_name", columnDefinition = "NVARCHAR(255)")
    private String fileName;

    @Column(
        name = "content_type",
        nullable = false,
        columnDefinition = "NVARCHAR(100)"
    )
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(
        name = "video_data",
        nullable = false,
        columnDefinition = "VARBINARY(MAX)"
    )
    private byte[] videoData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
