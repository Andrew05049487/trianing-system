package com.example.trainingsystems.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingSessionResultDtoJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void responseRoundTripPreservesResultFields() throws Exception {
        TrainingSessionResultDto source = new TrainingSessionResultDto(
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "CUSTOM",
            "custom-1",
            "手肘訓練",
            2,
            6,
            2,
            3,
            Instant.parse("2026-09-05T01:00:00Z"),
            Instant.parse("2026-09-05T01:00:30Z"),
            30L,
            "COMPLETED",
            new BigDecimal("100.00")
        );

        TrainingSessionResultDto decoded = mapper.readValue(
            mapper.writeValueAsString(source),
            TrainingSessionResultDto.class
        );

        assertThat(decoded).usingRecursiveComparison().isEqualTo(source);
    }
}
