package com.example.trainingsystems.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.trainingsystems.dto.TrainingHistoryRequest;
import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TrainingHistoryEvaluationService {

    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");

    private final ObjectMapper objectMapper;

    public TrainingHistoryEvaluationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void apply(
        TrainingHistoryRequest request,
        TrainingHistoryEntity entity
    ) {
        int validRepCount = request.getTemplateValidRepCount() == null
            ? 0
            : Math.max(0, request.getTemplateValidRepCount());

        entity.setAverageBodyScore(normalizeScore(request.getAverageBodyScore()));
        entity.setBodyRepScores(writeValue(
            request.getBodyRepScores() == null
                ? List.of()
                : request.getBodyRepScores().stream()
                    .map(value -> normalizeScore(BigDecimal.valueOf(value)))
                    .toList()
        ));
        entity.setTemplateScore(
            validRepCount > 0
                ? normalizeScore(request.getTemplateScore())
                : null
        );
        entity.setTemplateId(blankToNull(request.getTemplateId()));
        entity.setTemplateName(blankToNull(request.getTemplateName()));
        entity.setTemplateValidRepCount(validRepCount);
        entity.setTemplateRepScores(writeValue(
            request.getTemplateRepScores() == null
                ? List.of()
                : request.getTemplateRepScores().stream()
                    .map(this::normalizeScore)
                    .toList()
        ));
        entity.setTemplateDifferenceSummary(writeValue(
            request.getTemplateDifferenceSummary() == null
                ? List.of()
                : request.getTemplateDifferenceSummary()
        ));
    }

    public void addToResponse(
        TrainingHistoryEntity entity,
        Map<String, Object> response
    ) {
        response.put("averageBodyScore", entity.getAverageBodyScore());
        response.put("bodyRepScores", readScores(entity.getBodyRepScores()));
        response.put("templateScore", entity.getTemplateScore());
        response.put("templateId", entity.getTemplateId());
        response.put("templateName", entity.getTemplateName());
        response.put(
            "templateValidRepCount",
            entity.getTemplateValidRepCount() == null
                ? 0
                : entity.getTemplateValidRepCount()
        );
        response.put(
            "templateRepScores",
            readScores(entity.getTemplateRepScores())
        );
        response.put(
            "templateDifferenceSummary",
            readStrings(entity.getTemplateDifferenceSummary())
        );
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null) return null;
        return score.max(MIN_SCORE)
            .min(MAX_SCORE)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("訓練評分格式錯誤", error);
        }
    }

    private List<BigDecimal> readScores(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                json,
                new TypeReference<List<BigDecimal>>() {}
            );
        } catch (Exception error) {
            return List.of();
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                json,
                new TypeReference<List<String>>() {}
            );
        } catch (Exception error) {
            return List.of();
        }
    }
}
