package com.example.trainingsystems.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.trainingsystems.dto.TrainingHistoryRequest;
import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class TrainingHistoryEvaluationServiceTest {

    private final TrainingHistoryEvaluationService service =
        new TrainingHistoryEvaluationService(new ObjectMapper());

    @Test
    void roundTripsBasicAndValidTemplateScores() {
        TrainingHistoryRequest request = new TrainingHistoryRequest();
        request.setAverageBodyScore(new BigDecimal("87.456"));
        request.setBodyRepScores(List.of(84, 91));
        request.setTemplateScore(new BigDecimal("92.1"));
        request.setTemplateId(" template-1 ");
        request.setTemplateName(" 標準抬腳 ");
        request.setTemplateValidRepCount(2);
        request.setTemplateRepScores(List.of(
            new BigDecimal("90.25"),
            new BigDecimal("93.95")
        ));
        request.setTemplateDifferenceSummary(List.of("右膝差異", "軀幹差異"));

        TrainingHistoryEntity entity = new TrainingHistoryEntity();
        service.apply(request, entity);

        Map<String, Object> response = new HashMap<>();
        service.addToResponse(entity, response);

        assertThat(response)
            .containsEntry("averageBodyScore", new BigDecimal("87.46"))
            .containsEntry(
                "bodyRepScores",
                List.of(new BigDecimal("84.00"), new BigDecimal("91.00"))
            )
            .containsEntry("templateScore", new BigDecimal("92.10"))
            .containsEntry("templateId", "template-1")
            .containsEntry("templateName", "標準抬腳")
            .containsEntry("templateValidRepCount", 2)
            .containsEntry(
                "templateRepScores",
                List.of(new BigDecimal("90.25"), new BigDecimal("93.95"))
            )
            .containsEntry(
                "templateDifferenceSummary",
                List.of("右膝差異", "軀幹差異")
            );
    }

    @Test
    void invalidTemplateIsNeverStoredAsZeroScore() {
        TrainingHistoryRequest request = new TrainingHistoryRequest();
        request.setAverageBodyScore(new BigDecimal("100"));
        request.setTemplateScore(BigDecimal.ZERO);
        request.setTemplateValidRepCount(0);

        TrainingHistoryEntity entity = new TrainingHistoryEntity();
        service.apply(request, entity);

        assertThat(entity.getAverageBodyScore()).isEqualByComparingTo("100.00");
        assertThat(entity.getTemplateScore()).isNull();
        assertThat(entity.getTemplateValidRepCount()).isZero();
    }
}
