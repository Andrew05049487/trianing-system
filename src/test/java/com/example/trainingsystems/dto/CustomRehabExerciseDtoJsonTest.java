package com.example.trainingsystems.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomRehabExerciseDtoJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsFlutterJsonWithoutChangingNestedPoseData() throws Exception {
        String json = """
            {
              "id":"custom_1",
              "name":"Shoulder",
              "description":"Slowly",
              "createdByTherapistId":"7",
              "createdAt":"2026-09-04T01:02:03.000Z",
              "updatedAt":"2026-09-04T01:02:04.000Z",
              "repetitions":10,
              "sets":3,
              "holdSeconds":5.0,
              "restSeconds":30.0,
              "duration":1.0,
              "keyframes":[
                {"id":"kf_001","time":0.0,"jointRotations":{"rightShoulder":{"x":1.0,"y":2.0,"z":3.0}}},
                {"id":"kf_002","time":1.0,"jointRotations":{"rightShoulder":{"x":4.0,"y":5.0,"z":6.0}}}
              ],
              "evaluationRules":[]
            }
            """;

        CustomRehabExerciseDto dto = objectMapper.readValue(
            json,
            CustomRehabExerciseDto.class
        );
        String encoded = objectMapper.writeValueAsString(dto);
        CustomRehabExerciseDto decoded = objectMapper.readValue(
            encoded,
            CustomRehabExerciseDto.class
        );

        assertThat(decoded.getId()).isEqualTo("custom_1");
        assertThat(decoded.getCreatedByTherapistId()).isEqualTo("7");
        assertThat(decoded.getKeyframes()).isEqualTo(dto.getKeyframes());
        assertThat(decoded.getEvaluationRules()).isEqualTo(dto.getEvaluationRules());
    }
}
