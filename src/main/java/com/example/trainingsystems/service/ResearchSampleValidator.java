package com.example.trainingsystems.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Component
public class ResearchSampleValidator {
    public static final int MAX_JSON_BYTES = 300_000;
    private static final List<String> FEATURES = List.of(
        "peak_leg_height", "minimum_hip_angle_deg", "minimum_knee_angle_deg",
        "peak_abs_trunk_lean_deg", "duration_seconds"
    );
    private static final Set<String> ROOT_FIELDS = Set.of(
        "schemaVersion", "actionId", "sampleId", "subjectId", "movementSide",
        "cameraView", "capturedAt", "segment", "featureNames", "features", "frames"
    );
    private final ObjectMapper mapper;

    public ResearchSampleValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public ValidatedSample validate(JsonNode input) {
        if (input == null || !input.isObject()) throw invalid();
        try {
            if (mapper.writeValueAsBytes(input).length > MAX_JSON_BYTES) throw invalid();
        } catch (JsonProcessingException error) { throw invalid(); }
        input.fieldNames().forEachRemaining(name -> {
            if (!ROOT_FIELDS.contains(name)) throw invalid();
        });
        if (input.path("schemaVersion").asInt(-1) != 1 ||
            !"standing_knee_raise".equals(input.path("actionId").asText()) ||
            !input.path("sampleId").asText("").matches("[A-Za-z0-9_-]{1,100}") ||
            !Set.of("left", "right").contains(input.path("movementSide").asText()) ||
            !Set.of("front", "rear").contains(input.path("cameraView").asText())) {
            throw invalid();
        }
        Instant capturedAt;
        try { capturedAt = Instant.parse(input.path("capturedAt").asText()); }
        catch (DateTimeParseException error) { throw invalid(); }
        if (capturedAt.isAfter(Instant.now().plusSeconds(300)) ||
            capturedAt.isBefore(Instant.now().minusSeconds(365L * 86400))) throw invalid();

        JsonNode names = input.path("featureNames");
        JsonNode features = input.path("features");
        if (!names.isArray() || names.size() != FEATURES.size() ||
            !features.isArray() || features.size() != FEATURES.size()) throw invalid();
        for (int i = 0; i < FEATURES.size(); i++) {
            if (!FEATURES.get(i).equals(names.get(i).asText()) ||
                !finite(features.get(i), -360, 360)) throw invalid();
        }
        JsonNode frames = input.path("frames");
        if (!frames.isArray() || frames.size() < 4 || frames.size() > 80) throw invalid();
        ArrayNode safeFrames = mapper.createArrayNode();
        long first = -1, previous = -1;
        int[] required = input.path("movementSide").asText().equals("left")
            ? new int[]{5, 6, 11, 12, 13, 15}
            : new int[]{5, 6, 11, 12, 14, 16};
        for (JsonNode frame : frames) {
            JsonNode points = frame.path("landmarks");
            JsonNode scores = frame.path("confidence");
            JsonNode angles = frame.path("angles");
            JsonNode time = frame.path("timestampMs");
            if (!time.isIntegralNumber() || !points.isArray() || points.size() != 17 ||
                !scores.isArray() || scores.size() != 17 || !angles.isObject()) throw invalid();
            long timestamp = time.asLong();
            if (timestamp < 0 || timestamp <= previous) throw invalid();
            if (first < 0) first = timestamp;
            previous = timestamp;
            ArrayNode safePoints = mapper.createArrayNode();
            ArrayNode safeScores = mapper.createArrayNode();
            for (int i = 0; i < 17; i++) {
                JsonNode point = points.get(i);
                JsonNode score = scores.get(i);
                if (!point.isArray() || point.size() != 2 ||
                    !finite(point.get(0), -30, 30) || !finite(point.get(1), -30, 30) ||
                    !finite(score, 0, 1)) throw invalid();
                ArrayNode safePoint = mapper.createArrayNode();
                safePoint.add(point.get(0).asDouble());
                safePoint.add(point.get(1).asDouble());
                safePoints.add(safePoint);
                safeScores.add(score.asDouble());
            }
            for (int index : required) {
                if (scores.get(index).asDouble() < 0.3) throw invalid();
            }
            if (!finite(angles.path("hipDeg"), 0, 180) ||
                !finite(angles.path("kneeDeg"), 0, 180) ||
                !finite(angles.path("trunkLeanDeg"), -180, 180)) throw invalid();
            ObjectNode safeAngles = mapper.createObjectNode();
            safeAngles.put("hipDeg", angles.path("hipDeg").asDouble());
            safeAngles.put("kneeDeg", angles.path("kneeDeg").asDouble());
            safeAngles.put("trunkLeanDeg", angles.path("trunkLeanDeg").asDouble());
            ObjectNode safeFrame = mapper.createObjectNode();
            safeFrame.put("timestampMs", timestamp);
            safeFrame.set("landmarks", safePoints);
            safeFrame.set("confidence", safeScores);
            safeFrame.set("angles", safeAngles);
            safeFrames.add(safeFrame);
        }
        if (previous - first < 300 || previous - first > 8000 ||
            input.path("segment").path("startMs").asLong(-1) != first ||
            input.path("segment").path("endMs").asLong(-1) != previous ||
            Math.abs(features.get(4).asDouble() - (previous - first) / 1000.0) > 0.001) {
            throw invalid();
        }
        ObjectNode safe = mapper.createObjectNode();
        safe.put("schemaVersion", 1);
        safe.put("actionId", "standing_knee_raise");
        safe.put("sampleId", input.path("sampleId").asText());
        safe.put("movementSide", input.path("movementSide").asText());
        safe.put("cameraView", input.path("cameraView").asText());
        safe.put("capturedAt", capturedAt.toString());
        ObjectNode segment = mapper.createObjectNode();
        segment.put("startMs", first);
        segment.put("endMs", previous);
        safe.set("segment", segment);
        ArrayNode safeNames = mapper.createArrayNode();
        FEATURES.forEach(safeNames::add);
        safe.set("featureNames", safeNames);
        ArrayNode safeFeatures = mapper.createArrayNode();
        features.forEach(value -> safeFeatures.add(value.asDouble()));
        safe.set("features", safeFeatures);
        safe.set("frames", safeFrames);
        return new ValidatedSample(input.path("sampleId").asText(),
            input.path("movementSide").asText(), input.path("cameraView").asText(),
            capturedAt, safe);
    }

    private static boolean finite(JsonNode value, double min, double max) {
        return value.isNumber() && Double.isFinite(value.asDouble()) &&
            value.asDouble() >= min && value.asDouble() <= max;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_RESEARCH_SAMPLE");
    }

    public record ValidatedSample(String clientId, String side, String cameraView,
                                  Instant capturedAt, ObjectNode safePayload) {}
}
