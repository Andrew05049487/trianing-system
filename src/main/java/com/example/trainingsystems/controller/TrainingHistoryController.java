package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.TrainingHistoryRequest;
import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.UserRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public TrainingHistoryController(
            TrainingHistoryRepository repository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
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
                .body(toMap(saved));
    }

    /** 取得某使用者的全部自由訓練紀錄（新到舊）。 */
    @GetMapping("/training-history/{userId}")
    public List<Map<String, Object>> history(@PathVariable Long userId) {
        return repository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toMap)
                .toList();
    }

    // ── 內部工具 ──────────────────────────────────────────────

    /**
     * 回傳的欄位刻意對齊 app 端 TrainingRecord.toJson()，
     * 讓 app 收到後可以直接用 TrainingRecord.fromJson() 解析。
     * isSynced 一律回 true（既然是從雲端讀回來的，代表已在後端）。
     */
    private Map<String, Object> toMap(TrainingHistoryEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", e.getClientTimestamp());
        m.put("actionName", e.getActionName());
        m.put("difficulty", e.getDifficulty());
        m.put("durationSeconds", e.getDurationSeconds());
        m.put("targetReps", e.getTargetReps());
        m.put("mistakeLogs", readLogs(e.getMistakeLogs()));
        m.put("videoPath", null);
        m.put("isSynced", true);
        return m;
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