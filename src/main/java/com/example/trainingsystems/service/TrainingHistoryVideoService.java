package com.example.trainingsystems.service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;

@Service
public class TrainingHistoryVideoService {

    private static final String THERAPIST = "THERAPIST";

    /**
     * 每次從 DB 讀影片的最大 chunk。
     *
     * 不要一次 SELECT 整個 VARBINARY(MAX)。
     * 1 MB 對 Render 小記憶體 instance 比較安全。
     */
    private static final int READ_CHUNK_BYTES = 1024 * 1024;

    private final TrainingHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final UserBindingRepository bindingRepository;
    private final CustomExerciseIdentityService identityService;
    private final JdbcTemplate jdbcTemplate;
    private final long maxVideoBytes;

    public TrainingHistoryVideoService(
        TrainingHistoryRepository historyRepository,
        UserRepository userRepository,
        UserBindingRepository bindingRepository,
        CustomExerciseIdentityService identityService,
        JdbcTemplate jdbcTemplate,
        @Value("${training.history.video.max-bytes:104857600}")
        long maxVideoBytes
    ) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.bindingRepository = bindingRepository;
        this.identityService = identityService;
        this.jdbcTemplate = jdbcTemplate;
        this.maxVideoBytes = maxVideoBytes;
    }

    // ============================================================
    // Upload
    // ============================================================

    /**
     * 上傳 / 取代某筆 training history 的影片。
     *
     * 重要：
     *
     * 舊版：
     *     MultipartFile.getBytes()
     *         ↓
     *     byte[]
     *         ↓
     *     Hibernate
     *         ↓
     *     SQL Server
     *
     * 31.92 MB 的影片會在 JVM / Direct Memory 裡產生大型 buffer。
     *
     * 新版：
     *     MultipartFile.getInputStream()
     *         ↓
     *     PreparedStatement.setBinaryStream()
     *         ↓
     *     SQL Server VARBINARY(MAX)
     *
     * 不在 Java 程式裡建立完整影片 byte[]。
     */
    @Transactional
    public void upload(
        Long historyId,
        Long userId,
        MultipartFile file
    ) {
        TrainingHistoryEntity history =
            requireHistory(historyId);

        if (userId == null) {
            throw badRequest(
                "VIDEO_USER_REQUIRED",
                "userId 不可為空"
            );
        }

        if (!history.getUser()
            .getId()
            .equals(userId)) {

            throw forbidden(
                "影片只能綁定到該使用者自己的訓練紀錄"
            );
        }

        if (file == null ||
            file.isEmpty() ||
            file.getSize() <= 0) {

            throw badRequest(
                "VIDEO_EMPTY",
                "請選擇有效的影片檔案"
            );
        }

        final long fileSize =
            file.getSize();

        if (fileSize > maxVideoBytes) {
            throw tooLarge();
        }

        final String contentType =
            normalizeVideoType(
                file.getContentType()
            );

        final String fileName =
            normalizeFileName(
                file.getOriginalFilename()
            );

        final LocalDateTime now =
            LocalDateTime.now();

        final boolean alreadyExists =
            videoExists(historyId);

        try (
            InputStream inputStream =
                file.getInputStream()
        ) {
            if (alreadyExists) {
                updateExistingVideo(
                    historyId,
                    fileName,
                    contentType,
                    fileSize,
                    now,
                    inputStream
                );
            } else {
                insertNewVideo(
                    historyId,
                    fileName,
                    contentType,
                    fileSize,
                    now,
                    inputStream
                );
            }

        } catch (IOException error) {
            throw badRequest(
                "VIDEO_READ_FAILED",
                "無法讀取上傳的影片"
            );
        }
    }

    private void insertNewVideo(
        Long historyId,
        String fileName,
        String contentType,
        long fileSize,
        LocalDateTime now,
        InputStream inputStream
    ) {
        jdbcTemplate.update(
            connection -> {
                PreparedStatement ps =
                    connection.prepareStatement(
                        """
                        INSERT INTO dbo.training_history_video
                        (
                            history_id,
                            file_name,
                            content_type,
                            file_size,
                            video_data,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, NULL)
                        """
                    );

                ps.setLong(
                    1,
                    historyId
                );

                ps.setString(
                    2,
                    fileName
                );

                ps.setString(
                    3,
                    contentType
                );

                ps.setLong(
                    4,
                    fileSize
                );

                /*
                 * 關鍵：
                 * 不使用 file.getBytes()。
                 */
                ps.setBinaryStream(
                    5,
                    inputStream,
                    fileSize
                );

                ps.setObject(
                    6,
                    now
                );

                return ps;
            }
        );
    }

    private void updateExistingVideo(
        Long historyId,
        String fileName,
        String contentType,
        long fileSize,
        LocalDateTime now,
        InputStream inputStream
    ) {
        jdbcTemplate.update(
            connection -> {
                PreparedStatement ps =
                    connection.prepareStatement(
                        """
                        UPDATE dbo.training_history_video
                        SET
                            file_name = ?,
                            content_type = ?,
                            file_size = ?,
                            video_data = ?,
                            updated_at = ?
                        WHERE history_id = ?
                        """
                    );

                ps.setString(
                    1,
                    fileName
                );

                ps.setString(
                    2,
                    contentType
                );

                ps.setLong(
                    3,
                    fileSize
                );

                ps.setBinaryStream(
                    4,
                    inputStream,
                    fileSize
                );

                ps.setObject(
                    5,
                    now
                );

                ps.setLong(
                    6,
                    historyId
                );

                return ps;
            }
        );
    }

    private boolean videoExists(
        Long historyId
    ) {
        Integer count =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.training_history_video
                WHERE history_id = ?
                """,
                Integer.class,
                historyId
            );

        return count != null &&
            count > 0;
    }

    // ============================================================
    // Read authorization / metadata
    // ============================================================

    @Transactional(readOnly = true)
    public VideoMetadata requireReadableVideo(
        Long historyId,
        Long viewerUserId,
        String identityToken
    ) {
        User viewer =
            requireViewer(
                viewerUserId,
                identityToken
            );

        TrainingHistoryEntity history =
            requireHistory(historyId);

        Long ownerId =
            history.getUser().getId();

        if (!viewer.getId().equals(ownerId) &&
            !isBoundTherapist(
                viewer,
                ownerId
            )) {

            throw forbidden(
                "你沒有權限讀取這段患者訓練影片"
            );
        }

        List<VideoMetadata> rows =
            jdbcTemplate.query(
                """
                SELECT
                    file_name,
                    content_type,
                    file_size
                FROM dbo.training_history_video
                WHERE history_id = ?
                """,
                (rs, rowNum) ->
                    new VideoMetadata(
                        rs.getString(
                            "file_name"
                        ),
                        rs.getString(
                            "content_type"
                        ),
                        rs.getLong(
                            "file_size"
                        )
                    ),
                historyId
            );

        if (rows.isEmpty()) {
            throw notFound(
                "VIDEO_NOT_FOUND",
                "此訓練紀錄沒有影片"
            );
        }

        return rows.get(0);
    }

    // ============================================================
    // Range read
    // ============================================================

    /**
     * 只讀取需要的範圍，不讀整支影片。
     *
     * SQL Server SUBSTRING 對 VARBINARY 使用 1-based offset。
     */
    @Transactional(readOnly = true)
    public byte[] readRange(
        Long historyId,
        long start,
        int requestedLength
    ) {
        if (start < 0 ||
            requestedLength <= 0) {

            return new byte[0];
        }

        /*
         * 即使 Controller 要求很大的 Range，
         * 單次最多只讀 1MB。
         */
        int length =
            Math.min(
                requestedLength,
                READ_CHUNK_BYTES
            );

        /*
         * SQL Server SUBSTRING 是 1-based。
         */
        long sqlOffset =
            start + 1L;

        byte[] result =
            jdbcTemplate.queryForObject(
                """
                SELECT
                    SUBSTRING(
                        video_data,
                        ?,
                        ?
                    )
                FROM dbo.training_history_video
                WHERE history_id = ?
                """,
                byte[].class,
                sqlOffset,
                length,
                historyId
            );

        return result == null
            ? new byte[0]
            : result;
    }

    public int getReadChunkBytes() {
        return READ_CHUNK_BYTES;
    }

    // ============================================================
    // Existing authorization helpers
    // ============================================================

    private TrainingHistoryEntity requireHistory(
        Long historyId
    ) {
        if (historyId == null) {
            throw badRequest(
                "INVALID_HISTORY_ID",
                "historyId 不可為空"
            );
        }

        return historyRepository
            .findById(historyId)
            .orElseThrow(
                () -> notFound(
                    "TRAINING_HISTORY_NOT_FOUND",
                    "找不到訓練歷史紀錄"
                )
            );
    }

    private User requireViewer(
        Long userId,
        String identityToken
    ) {
        if (userId == null ||
            identityToken == null ||
            identityToken.isBlank()) {

            throw unauthorized(
                "缺少登入身份或 identity token"
            );
        }

        User user =
            userRepository
                .findById(userId)
                .orElseThrow(
                    () ->
                        unauthorized(
                            "登入身份無效"
                        )
                );

        if (!identityService.isConfigured()) {
            throw new TrainingHistoryApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "VIDEO_IDENTITY_UNAVAILABLE",
                "影片 identity 驗證尚未設定"
            );
        }

        if (!identityService.isValid(
            user,
            identityToken
        )) {
            throw forbidden(
                "Identity token 無效"
            );
        }

        return user;
    }

    private boolean isBoundTherapist(
        User viewer,
        Long patientId
    ) {
        return viewer.getRole() != null &&
            THERAPIST.equalsIgnoreCase(
                viewer.getRole()
            ) &&
            bindingRepository
                .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                    patientId,
                    viewer.getId(),
                    THERAPIST
                );
    }

    private String normalizeVideoType(
        String raw
    ) {
        if (raw == null) {
            throw unsupported(
                "只接受 video/* 影片檔案"
            );
        }

        String normalized =
            raw.split(";", 2)[0]
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if (!normalized.startsWith(
                "video/"
            ) ||
            normalized.length() <= 6) {

            throw unsupported(
                "只接受 video/* 影片檔案"
            );
        }

        return normalized;
    }

    private String normalizeFileName(
        String raw
    ) {
        if (raw == null ||
            raw.isBlank()) {

            return null;
        }

        String value =
            raw.replace(
                '\\',
                '/'
            );

        value =
            value.substring(
                value.lastIndexOf('/') + 1
            ).trim();

        if (value.isEmpty()) {
            return null;
        }

        return value.length() <= 255
            ? value
            : value.substring(
                value.length() - 255
            );
    }

    // ============================================================
    // Exceptions
    // ============================================================

    private TrainingHistoryApiException unauthorized(
        String message
    ) {
        return new TrainingHistoryApiException(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHORIZED",
            message
        );
    }

    private TrainingHistoryApiException forbidden(
        String message
    ) {
        return new TrainingHistoryApiException(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            message
        );
    }

    private TrainingHistoryApiException badRequest(
        String code,
        String message
    ) {
        return new TrainingHistoryApiException(
            HttpStatus.BAD_REQUEST,
            code,
            message
        );
    }

    private TrainingHistoryApiException unsupported(
        String message
    ) {
        return new TrainingHistoryApiException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_VIDEO_TYPE",
            message
        );
    }

    private TrainingHistoryApiException tooLarge() {
        return new TrainingHistoryApiException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "VIDEO_TOO_LARGE",
            "訓練影片超過允許大小"
        );
    }

    private TrainingHistoryApiException notFound(
        String code,
        String message
    ) {
        return new TrainingHistoryApiException(
            HttpStatus.NOT_FOUND,
            code,
            message
        );
    }

    // ============================================================
    // DTO
    // ============================================================

    public record VideoMetadata(
        String fileName,
        String contentType,
        long fileSize
    ) {
    }
}