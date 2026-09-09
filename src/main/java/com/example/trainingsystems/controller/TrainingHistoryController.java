package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.TrainingHistoryRequest;
import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.TrainingHistoryVideoRepository;
import com.example.trainingsystems.repository.UserRepository;
import com.example.trainingsystems.service.TrainingHistoryVideoService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 自由訓練（app「訓練進步曲線」）的歷史紀錄端點。
 *
 * 跟 ExerciseController(/api/exercise/*)
 * 與 TrainingSessionResultController(/api/training-results)
 * 完全獨立，不影響它們的既有行為。
 *
 * 上傳走「病患自己在歷史頁指定上傳」的方式：
 * 離線時 app 只存本機，
 * 有網路時才呼叫這裡。
 *
 * (userId, clientTimestamp) 唯一，
 * 所以同一筆重複上傳只會更新、不會產生重複列。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TrainingHistoryController {

    /**
     * 單次最多回傳 1 MB。
     *
     * 即使 Flutter / video_player 要求：
     *
     * Range: bytes=0-
     *
     * 後端也不會一次把 30MB / 50MB / 100MB
     * 整支影片載入 JVM。
     */
    private static final long MAX_RANGE_BYTES =
        1024L * 1024L;


    private final TrainingHistoryRepository repository;

    private final TrainingHistoryVideoRepository videoRepository;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    private final TrainingHistoryVideoService videoService;


    public TrainingHistoryController(
        TrainingHistoryRepository repository,
        TrainingHistoryVideoRepository videoRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        TrainingHistoryVideoService videoService
    ) {
        this.repository = repository;
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.videoService = videoService;
    }


    // ============================================================
    // Training History Metadata
    // ============================================================

    /**
     * 上傳（或更新）單筆自由訓練紀錄。
     */
    @PostMapping("/training-history")
    public ResponseEntity<Map<String, Object>> save(
        @RequestBody TrainingHistoryRequest request
    ) {

        if (request.getUserId() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "userId 不可為空"
            );
        }

        if (
            request.getClientTimestamp() == null ||
            request.getClientTimestamp().isBlank()
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "clientTimestamp 不可為空"
            );
        }

        if (
            request.getActionName() == null ||
            request.getActionName().isBlank()
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "actionName 不可為空"
            );
        }


        User user =
            userRepository
                .findById(request.getUserId())
                .orElseThrow(
                    () -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "找不到使用者"
                    )
                );


        /*
         * 冪等 upsert：
         *
         * 同一個：
         *
         * (userId, clientTimestamp)
         *
         * 已存在就更新原本那一列。
         */
        TrainingHistoryEntity entity =
            repository
                .findByUser_IdAndClientTimestamp(
                    user.getId(),
                    request.getClientTimestamp()
                )
                .orElseGet(
                    TrainingHistoryEntity::new
                );


        boolean isNew =
            entity.getId() == null;


        List<String> logs =
            request.getMistakeLogs() == null
                ? List.of()
                : request.getMistakeLogs();


        entity.setUser(user);

        entity.setActionName(
            request.getActionName()
        );


        entity.setDifficulty(
            request.getDifficulty() != null
                ? request.getDifficulty()
                : 1
        );


        entity.setDurationSeconds(
            request.getDurationSeconds() != null
                ? request.getDurationSeconds()
                : 0
        );


        entity.setCompletedReps(
            request.getCompletedReps() != null
                ? Math.max(
                    0,
                    request.getCompletedReps()
                )
                : 0
        );


        entity.setTargetReps(
            request.getTargetReps() != null
                ? request.getTargetReps()
                : 0
        );


        entity.setMistakeCount(
            logs.size()
        );


        entity.setMistakeLogs(
            writeLogs(logs)
        );


        entity.setClientTimestamp(
            request.getClientTimestamp()
        );


        if (isNew) {
            entity.setCreatedAt(
                LocalDateTime.now()
            );
        }


        TrainingHistoryEntity saved =
            repository.save(entity);


        boolean hasVideo =
            videoRepository.existsById(
                saved.getId()
            );


        return ResponseEntity
            .status(
                isNew
                    ? HttpStatus.CREATED
                    : HttpStatus.OK
            )
            .body(
                toMap(
                    saved,
                    hasVideo
                )
            );
    }


    /**
     * 取得某使用者的全部自由訓練紀錄。
     *
     * 新 → 舊。
     */
    @GetMapping("/training-history/{userId}")
    public List<Map<String, Object>> history(
        @PathVariable Long userId
    ) {

        List<TrainingHistoryEntity> history =
            repository
                .findByUser_IdOrderByCreatedAtDesc(
                    userId
                );


        if (history.isEmpty()) {
            return List.of();
        }


        Set<Long> videoIds =
            new HashSet<>(
                videoRepository
                    .findExistingHistoryIds(
                        history
                            .stream()
                            .map(
                                TrainingHistoryEntity::getId
                            )
                            .toList()
                    )
            );


        return history
            .stream()
            .map(
                entity ->
                    toMap(
                        entity,
                        videoIds.contains(
                            entity.getId()
                        )
                    )
            )
            .toList();
    }


    // ============================================================
    // Video Upload
    // ============================================================

    /**
     * 上傳或取代一筆歷史紀錄的一對一影片。
     *
     * 真正 binary 寫入由 TrainingHistoryVideoService
     * 使用 JDBC setBinaryStream() 完成。
     *
     * Controller 不碰 byte[]。
     */
    @PostMapping(
        value = "/training-history/{historyId}/video",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> uploadVideo(
        @PathVariable Long historyId,

        @RequestParam(
            value = "userId",
            required = false
        )
        Long userId,

        @RequestPart("file")
        MultipartFile file
    ) {

        videoService.upload(
            historyId,
            userId,
            file
        );


        return ResponseEntity
            .noContent()
            .build();
    }


    // ============================================================
    // Video Streaming
    // ============================================================

    /**
     * HTTP Range 串流。
     *
     * Flutter video_player 可以透過：
     *
     * Range: bytes=xxxxx-yyyyy
     *
     * 分段取得影片。
     *
     * 重要：
     *
     * 不再：
     *
     * SELECT 整支 VARBINARY(MAX)
     *      ↓
     * byte[]
     *      ↓
     * Arrays.copyOfRange()
     *
     * 而是：
     *
     * HTTP Range
     *      ↓
     * SQL Server SUBSTRING(video_data,...)
     *      ↓
     * 最多 1 MB byte[]
     *      ↓
     * HTTP 206
     */
    @GetMapping(
        "/training-history/{historyId}/video"
    )
    public ResponseEntity<byte[]> readVideo(

        @PathVariable
        Long historyId,

        @RequestHeader(
            value = "Range",
            required = false
        )
        String rangeHeader,

        @RequestHeader(
            value = "X-User-Id",
            required = false
        )
        Long viewerUserId,

        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        )
        String identityToken
    ) {

        /*
         * 先驗證：
         *
         * 1. viewer 身份
         * 2. owner / therapist binding
         * 3. history 是否存在
         * 4. video 是否存在
         *
         * 這個方法只 SELECT metadata，
         * 不會讀 video_data。
         */
        TrainingHistoryVideoService.VideoMetadata video =
            videoService.requireReadableVideo(
                historyId,
                viewerUserId,
                identityToken
            );


        long totalLength =
            video.fileSize();


        HttpHeaders headers =
            videoHeaders(video);


        headers.set(
            HttpHeaders.ACCEPT_RANGES,
            "bytes"
        );


        /*
         * 理論上不應該存在 0 bytes 的影片，
         * upload 已經擋掉。
         *
         * 這裡仍做保護。
         */
        if (totalLength <= 0) {

            headers.setContentLength(0);

            return new ResponseEntity<>(
                new byte[0],
                headers,
                HttpStatus.OK
            );
        }


        // ========================================================
        // 沒有 Range
        // ========================================================

        /*
         * 很多播放器第一次 request 可能不帶 Range。
         *
         * 千萬不要因此回整支影片。
         *
         * 我們只回第一個 chunk。
         */
        if (
            rangeHeader == null ||
            rangeHeader.isBlank()
        ) {

            int length =
                (int) Math.min(
                    MAX_RANGE_BYTES,
                    totalLength
                );


            byte[] chunk =
                videoService.readRange(
                    historyId,
                    0,
                    length
                );


            long actualEnd =
                chunk.length == 0
                    ? 0
                    : chunk.length - 1L;


            /*
             * 如果整支影片 <= 1MB，
             * 可以直接當正常完整回應。
             */
            if (
                totalLength <= MAX_RANGE_BYTES &&
                chunk.length == totalLength
            ) {

                headers.setContentLength(
                    chunk.length
                );


                return new ResponseEntity<>(
                    chunk,
                    headers,
                    HttpStatus.OK
                );
            }


            /*
             * 大影片即使 request 沒帶 Range，
             * 仍只回第一段。
             */
            headers.set(
                HttpHeaders.CONTENT_RANGE,
                "bytes 0-" +
                    actualEnd +
                    "/" +
                    totalLength
            );


            headers.setContentLength(
                chunk.length
            );


            return new ResponseEntity<>(
                chunk,
                headers,
                HttpStatus.PARTIAL_CONTENT
            );
        }


        // ========================================================
        // 有 Range
        // ========================================================

        ByteRange requestedRange =
            parseRange(
                rangeHeader,
                totalLength
            );


        if (requestedRange == null) {

            headers.set(
                HttpHeaders.CONTENT_RANGE,
                "bytes */" +
                    totalLength
            );


            headers.setContentLength(0);


            return new ResponseEntity<>(
                new byte[0],
                headers,
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE
            );
        }


        /*
         * Client 可能要求：
         *
         * bytes=0-
         *
         * 代表「從 0 到影片最後」。
         *
         * 我們不能真的一次回 31MB。
         *
         * 所以強制限制為最多 1MB。
         */
        long start =
            requestedRange.start();


        long requestedEnd =
            requestedRange.endInclusive();


        long cappedEnd =
            Math.min(
                requestedEnd,
                start +
                    MAX_RANGE_BYTES -
                    1
            );


        long wantedLength =
            cappedEnd -
                start +
                1;


        int safeLength =
            Math.toIntExact(
                wantedLength
            );


        /*
         * SQL Server：
         *
         * SUBSTRING(video_data,...)
         *
         * 只取這一小段。
         */
        byte[] chunk =
            videoService.readRange(
                historyId,
                start,
                safeLength
            );


        /*
         * DB 理論上應回 safeLength，
         * 但還是依實際 byte[] 長度計算 response end。
         */
        long actualEnd;

        if (chunk.length == 0) {
            actualEnd = start;
        } else {
            actualEnd =
                start +
                    chunk.length -
                    1L;
        }


        headers.set(
            HttpHeaders.CONTENT_RANGE,
            "bytes " +
                start +
                "-" +
                actualEnd +
                "/" +
                totalLength
        );


        headers.setContentLength(
            chunk.length
        );


        return new ResponseEntity<>(
            chunk,
            headers,
            HttpStatus.PARTIAL_CONTENT
        );
    }


    // ============================================================
    // JSON Mapping
    // ============================================================

    /**
     * 回傳欄位對齊 app 端 TrainingRecord.toJson()。
     *
     * App 可以直接：
     *
     * TrainingRecord.fromJson()
     */
    private Map<String, Object> toMap(
        TrainingHistoryEntity e,
        boolean hasVideo
    ) {

        Map<String, Object> m =
            new HashMap<>();


        m.put(
            "id",
            e.getId()
        );


        m.put(
            "timestamp",
            e.getClientTimestamp()
        );


        m.put(
            "actionName",
            e.getActionName()
        );


        m.put(
            "difficulty",
            e.getDifficulty()
        );


        m.put(
            "durationSeconds",
            e.getDurationSeconds()
        );


        m.put(
            "completedReps",
            e.getCompletedReps()
        );


        m.put(
            "targetReps",
            e.getTargetReps()
        );


        m.put(
            "mistakeLogs",
            readLogs(
                e.getMistakeLogs()
            )
        );


        /*
         * 手機本機路徑不能存在後端。
         */
        m.put(
            "videoPath",
            null
        );


        m.put(
            "hasVideo",
            hasVideo
        );


        m.put(
            "videoUrl",
            hasVideo
                ? "/api/training-history/" +
                    e.getId() +
                    "/video"
                : null
        );


        /*
         * 從 Cloud GET 回來的資料，
         * 本身當然已經是 metadata synced。
         */
        m.put(
            "isSynced",
            true
        );


        /*
         * 有影片才代表 video synced。
         */
        m.put(
            "isVideoSynced",
            hasVideo
        );


        return m;
    }


    // ============================================================
    // Video Headers
    // ============================================================

    private HttpHeaders videoHeaders(
        TrainingHistoryVideoService.VideoMetadata video
    ) {

        HttpHeaders headers =
            new HttpHeaders();


        headers.setContentType(
            MediaType.parseMediaType(
                video.contentType()
            )
        );


        if (
            video.fileName() != null &&
            !video.fileName().isBlank()
        ) {

            headers.setContentDisposition(
                ContentDisposition
                    .inline()
                    .filename(
                        video.fileName(),
                        StandardCharsets.UTF_8
                    )
                    .build()
            );
        }


        return headers;
    }


    // ============================================================
    // HTTP Range Parser
    // ============================================================

    /**
     * 支援：
     *
     * bytes=0-
     *
     * bytes=0-999999
     *
     * bytes=1000000-1999999
     *
     * bytes=-500000
     *
     * 不支援 multi-range：
     *
     * bytes=0-100,200-300
     */
    private ByteRange parseRange(
        String raw,
        long totalLength
    ) {

        if (
            raw == null ||
            !raw.startsWith("bytes=") ||
            raw.contains(",") ||
            totalLength <= 0
        ) {
            return null;
        }


        String value =
            raw
                .substring(
                    "bytes=".length()
                )
                .trim();


        int dash =
            value.indexOf('-');


        if (dash < 0) {
            return null;
        }


        try {

            // ====================================================
            // suffix range
            // bytes=-500000
            // ====================================================

            if (dash == 0) {

                String suffixText =
                    value.substring(1);


                if (suffixText.isBlank()) {
                    return null;
                }


                long suffixLength =
                    Long.parseLong(
                        suffixText
                    );


                if (suffixLength <= 0) {
                    return null;
                }


                long start =
                    Math.max(
                        0,
                        totalLength -
                            suffixLength
                    );


                return new ByteRange(
                    start,
                    totalLength - 1L
                );
            }


            // ====================================================
            // normal range
            // ====================================================

            long start =
                Long.parseLong(
                    value.substring(
                        0,
                        dash
                    )
                );


            if (
                start < 0 ||
                start >= totalLength
            ) {
                return null;
            }


            long end;


            /*
             * bytes=12345-
             */
            if (
                dash ==
                value.length() - 1
            ) {

                end =
                    totalLength - 1L;

            } else {

                end =
                    Long.parseLong(
                        value.substring(
                            dash + 1
                        )
                    );
            }


            if (end < start) {
                return null;
            }


            end =
                Math.min(
                    end,
                    totalLength - 1L
                );


            return new ByteRange(
                start,
                end
            );


        } catch (
            NumberFormatException error
        ) {

            return null;
        }
    }


    private record ByteRange(
        long start,
        long endInclusive
    ) {
    }


    // ============================================================
    // Mistake Logs JSON
    // ============================================================

    private String writeLogs(
        List<String> logs
    ) {

        try {

            return objectMapper
                .writeValueAsString(
                    logs
                );

        } catch (Exception ex) {

            return "[]";
        }
    }


    private List<String> readLogs(
        String json
    ) {

        if (
            json == null ||
            json.isBlank()
        ) {

            return List.of();
        }


        try {

            return objectMapper
                .readValue(
                    json,
                    new TypeReference<List<String>>() {}
                );

        } catch (Exception ex) {

            return List.of();
        }
    }
}