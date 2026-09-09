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

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自由訓練（app「訓練進步曲線」）的歷史紀錄端點。
 *
 * 跟 ExerciseController(/api/exercise/*) 與 TrainingSessionResultController
 * (/api/training-results) 完全獨立，不影響它們的既有行為。
 *
 * 上傳走「病患自己在歷史頁指定上傳」的方式：離線時 app 只存本機，
 * 有網路時才呼叫這裡。(userId, clientTimestamp) 唯一，所以同一筆
 * 重複上傳只會更新、不會產生重複列。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TrainingHistoryController {

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
            TrainingHistoryVideoService videoService) {
        this.repository = repository;
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.videoService = videoService;
    }

    /** 上傳（或更新）單筆自由訓練紀錄。 */
    @PostMapping("/training-history")
    public ResponseEntity<Map<String, Object>> save(
            @RequestBody TrainingHistoryRequest request) {

        if (request.getUserId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "userId 不可為空");
        }
        if (request.getClientTimestamp() == null
                || request.getClientTimestamp().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "clientTimestamp 不可為空");
        }
        if (request.getActionName() == null || request.getActionName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "actionName 不可為空");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "找不到使用者"));

        // 冪等 upsert：同一個 (userId, clientTimestamp) 已存在就更新那一列
        TrainingHistoryEntity entity = repository
                .findByUser_IdAndClientTimestamp(
                        user.getId(), request.getClientTimestamp())
                .orElseGet(TrainingHistoryEntity::new);
        boolean isNew = entity.getId() == null;

        List<String> logs = request.getMistakeLogs() == null
                ? List.of()
                : request.getMistakeLogs();

        entity.setUser(user);
        entity.setActionName(request.getActionName());
        entity.setDifficulty(
                request.getDifficulty() != null ? request.getDifficulty() : 1);
        entity.setDurationSeconds(
                request.getDurationSeconds() != null
                        ? request.getDurationSeconds()
                        : 0);
        entity.setCompletedReps(
                request.getCompletedReps() != null
                        ? Math.max(0, request.getCompletedReps())
                        : 0);
        entity.setTargetReps(
                request.getTargetReps() != null ? request.getTargetReps() : 0);
        entity.setMistakeCount(logs.size());
        entity.setMistakeLogs(writeLogs(logs));
        entity.setClientTimestamp(request.getClientTimestamp());
        if (isNew) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        TrainingHistoryEntity saved = repository.save(entity);

        return ResponseEntity
                .status(isNew ? HttpStatus.CREATED : HttpStatus.OK)
                .body(toMap(saved, videoRepository.existsById(saved.getId())));
    }

    /** 取得某使用者的全部自由訓練紀錄（新到舊）。 */
    @GetMapping("/training-history/{userId}")
    public List<Map<String, Object>> history(@PathVariable Long userId) {
        List<TrainingHistoryEntity> history =
                repository.findByUser_IdOrderByCreatedAtDesc(userId);
        if (history.isEmpty()) return List.of();
        Set<Long> videoIds = new HashSet<>(
                videoRepository.findExistingHistoryIds(
                        history.stream().map(TrainingHistoryEntity::getId).toList()));
        return history.stream()
                .map(entity -> toMap(entity, videoIds.contains(entity.getId())))
                .toList();
    }

    /** 上傳或取代一筆歷史紀錄的一對一影片。 */
    @PostMapping(
        value = "/training-history/{historyId}/video",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> uploadVideo(
        @PathVariable Long historyId,
        @RequestParam(value = "userId", required = false) Long userId,
        @RequestPart("file") MultipartFile file
    ) {
        videoService.upload(historyId, userId, file);
        return ResponseEntity.noContent().build();
    }

    /** 支援單一 HTTP Range，供 Flutter video_player 串流與拖曳進度。 */
    @GetMapping("/training-history/{historyId}/video")
    public ResponseEntity<byte[]> readVideo(
        @PathVariable Long historyId,
        @RequestHeader(value = "Range", required = false) String rangeHeader,
        @RequestHeader(value = "X-User-Id", required = false) Long viewerUserId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        TrainingHistoryVideoService.VideoContent video = videoService.read(
            historyId,
            viewerUserId,
            identityToken
        );
        byte[] bytes = video.bytes();
        HttpHeaders headers = videoHeaders(video);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (rangeHeader == null || rangeHeader.isBlank()) {
            headers.setContentLength(bytes.length);
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        }

        ByteRange range = parseRange(rangeHeader, bytes.length);
        if (range == null) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + bytes.length);
            headers.setContentLength(0);
            return new ResponseEntity<>(new byte[0], headers,
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }

        byte[] partial = Arrays.copyOfRange(
            bytes,
            Math.toIntExact(range.start()),
            Math.toIntExact(range.endInclusive() + 1)
        );
        headers.set(
            HttpHeaders.CONTENT_RANGE,
            "bytes " + range.start() + "-" + range.endInclusive() +
                "/" + bytes.length
        );
        headers.setContentLength(partial.length);
        return new ResponseEntity<>(partial, headers, HttpStatus.PARTIAL_CONTENT);
    }

    // ── 內部工具 ──────────────────────────────────────────────

    /**
     * 回傳的欄位刻意對齊 app 端 TrainingRecord.toJson()，
     * 讓 app 收到後可以直接用 TrainingRecord.fromJson() 解析。
     * isSynced 一律回 true（既然是從雲端讀回來的，代表已在後端）。
     */
    private Map<String, Object> toMap(
            TrainingHistoryEntity e,
            boolean hasVideo) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("timestamp", e.getClientTimestamp());
        m.put("actionName", e.getActionName());
        m.put("difficulty", e.getDifficulty());
        m.put("durationSeconds", e.getDurationSeconds());
        m.put("completedReps", e.getCompletedReps());
        m.put("targetReps", e.getTargetReps());
        m.put("mistakeLogs", readLogs(e.getMistakeLogs()));
        m.put("videoPath", null);
        m.put("hasVideo", hasVideo);
        m.put("videoUrl", hasVideo
                ? "/api/training-history/" + e.getId() + "/video"
                : null);
        m.put("isSynced", true);
        m.put("isVideoSynced", true);
        return m;
    }

    private HttpHeaders videoHeaders(
            TrainingHistoryVideoService.VideoContent video) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(video.contentType()));
        if (video.fileName() != null && !video.fileName().isBlank()) {
            headers.setContentDisposition(ContentDisposition.inline()
                .filename(video.fileName(), StandardCharsets.UTF_8)
                .build());
        }
        return headers;
    }

    private ByteRange parseRange(String raw, int totalLength) {
        if (!raw.startsWith("bytes=") || raw.contains(",") || totalLength <= 0) {
            return null;
        }
        String value = raw.substring("bytes=".length()).trim();
        int dash = value.indexOf('-');
        if (dash < 0) return null;
        try {
            if (dash == 0) {
                long suffixLength = Long.parseLong(value.substring(1));
                if (suffixLength <= 0) return null;
                long start = Math.max(0, totalLength - suffixLength);
                return new ByteRange(start, totalLength - 1L);
            }
            long start = Long.parseLong(value.substring(0, dash));
            if (start < 0 || start >= totalLength) return null;
            long end = dash == value.length() - 1
                ? totalLength - 1L
                : Long.parseLong(value.substring(dash + 1));
            if (end < start) return null;
            return new ByteRange(start, Math.min(end, totalLength - 1L));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private record ByteRange(long start, long endInclusive) {
    }

    private String writeLogs(List<String> logs) {
        try {
            return objectMapper.writeValueAsString(logs);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readLogs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }
}
