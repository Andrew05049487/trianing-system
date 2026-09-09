package com.example.trainingsystems.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "training_history_video")
@Data
public class TrainingHistoryVideoEntity {

    @Id
    @Column(
        name = "history_id",
        nullable = false
    )
    private Long historyId;

    @Column(
        name = "file_name",
        columnDefinition = "NVARCHAR(255)"
    )
    private String fileName;

    @Column(
        name = "content_type",
        nullable = false,
        columnDefinition = "NVARCHAR(100)"
    )
    private String contentType;

    @Column(
        name = "file_size",
        nullable = false
    )
    private Long fileSize;

    /**
     * 影片本體不要在一般 metadata 查詢中主動載入。
     *
     * 正式影片上傳 / 播放已改走 JDBC streaming，
     * 不再依賴這個 byte[] 做影片讀寫。
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(
        name = "video_data",
        nullable = false,
        columnDefinition = "VARBINARY(MAX)"
    )
    private byte[] videoData;

    @Column(
        name = "created_at",
        nullable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}