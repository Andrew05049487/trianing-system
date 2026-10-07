package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Schema 4 is a bounded skeleton review record, never an ML feature record. */
public final class ResearchBodyReviewValidator {
    private static final Set<String> ROOT = Set.of("sampleId", "subjectId", "sessionId", "attemptId",
        "schemaVersion", "modality", "source", "platform", "exerciseType", "exerciseId",
        "actionId", "actionDefinitionVersion", "poseModelVersion", "coordinateTransformVersion",
        "streamSessionId", "frameId", "timestampOrigin", "movementSide", "cameraView",
        "capturedAt", "featureNames", "features", "featuresStatus", "duration",
        "terminationReason", "setIndex", "completedRepsBefore", "completedRepsAfter",
        "intendedRepetition", "trackingQuality", "frames", "resampleOfSampleId", "movementMode", "difficulty");
    private static final Set<String> FRAME = Set.of("frameId", "streamSessionId", "timestampMs",
        "timestampOrigin", "captureTimestamp", "imageWidth", "imageHeight", "source",
        "poseModelVersion", "coordinateTransformVersion", "mirrored", "rotationDegrees",
        "scoreSemantics", "keypoints", "scores", "validity");
    private static final String POSE = "rtmpose-wholebody-133-v1";
    private static final String TRANSFORM = "rtmpose-image-normalized-v1";
    private static final String SCORES = "simcc_peak_mean_uncalibrated";
    private final ObjectMapper mapper;

    public ResearchBodyReviewValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public ResearchSampleValidator.ValidatedSample validate(JsonNode input, boolean recent) {
        only(input, ROOT);
        if (recent && input.has("subjectId")) fail();
        if (input.path("schemaVersion").asInt(-1) != 4 ||
            !"body".equals(text(input, "modality")) ||
            !"not_applicable".equals(text(input, "featuresStatus"))) fail();
        var definition = ResearchActionRegistry.BODY_REVIEW.get(text(input, "actionId"));
        if (definition == null || !definition.version().equals(text(input, "actionDefinitionVersion"))) fail();
        if (!emptyArray(input.get("featureNames")) || !emptyArray(input.get("features"))) fail();
        choice(input, "source", "phone", "tv_pi");
        choice(input, "platform", "android_phone", "android_tv");
        choice(input, "exerciseType", "DEFAULT");
        choice(input, "cameraView", "front", "rear");
        choice(input, "timestampOrigin", "phone_receive_monotonic", "tv_receive_monotonic");
        String source = text(input, "source");
        if (source.equals("phone") != (text(input, "platform").equals("android_phone") &&
            text(input, "timestampOrigin").equals("phone_receive_monotonic"))) fail();
        if (source.equals("tv_pi") != (text(input, "platform").equals("android_tv") &&
            text(input, "timestampOrigin").equals("tv_receive_monotonic"))) fail();
        String side = text(input, "movementSide");
        if (Set.of("raise_both_arms", "elbow_forward", "sit_to_stand").contains(definition.actionId())) {
            if (!"bilateral".equals(side) || input.has("movementMode")) fail();
        } else if (!Set.of("left", "right").contains(side)) fail();
        if (input.has("movementMode") &&
            (!"lateral_step".equals(definition.actionId()) ||
             !Set.of("simple", "hard").contains(text(input, "movementMode")))) fail();
        if (input.has("difficulty") &&
            !Set.of("初級", "中級", "高級").contains(text(input, "difficulty"))) fail();
        for (String key : List.of("sampleId", "sessionId", "attemptId", "exerciseId", "streamSessionId"))
            if (!text(input, key).matches("[A-Za-z0-9_-]{1,100}")) fail();
        if (input.has("resampleOfSampleId") &&
            !text(input, "resampleOfSampleId").matches("[0-9a-fA-F-]{36}")) fail();
        if (!POSE.equals(text(input, "poseModelVersion")) ||
            !TRANSFORM.equals(text(input, "coordinateTransformVersion")) ||
            !"SCORED_REP".equals(text(input, "terminationReason"))) fail();
        int before = integer(input.get("completedRepsBefore"), 0, 100000);
        if (integer(input.get("completedRepsAfter"), before + 1, before + 1) != before + 1 ||
            integer(input.get("intendedRepetition"), before + 1, before + 1) != before + 1) fail();
        integer(input.get("setIndex"), 1, 10000);
        Instant captured;
        try { captured = Instant.parse(text(input, "capturedAt")); }
        catch (DateTimeParseException error) { throw invalid(); }
        if (recent && (captured.isAfter(Instant.now().plusSeconds(300)) ||
            captured.isBefore(Instant.now().minusSeconds(365L * 86400)))) fail();
        JsonNode frames = input.get("frames");
        if (frames == null || !frames.isArray() || frames.size() < 2 || frames.size() > 200) fail();
        long first = -1, previousTime = -1, previousId = -1;
        int validFrames = 0;
        for (JsonNode frame : frames) {
            only(frame, FRAME);
            long timestamp = nonnegative(frame.get("timestampMs"));
            long frameId = nonnegative(frame.get("frameId"));
            if (timestamp <= previousTime || frameId <= previousId) fail();
            if (first < 0) first = timestamp;
            previousTime = timestamp;
            previousId = frameId;
            for (String key : List.of("streamSessionId", "timestampOrigin", "source",
                "poseModelVersion", "coordinateTransformVersion"))
                if (!text(input, key).equals(text(frame, key))) fail();
            if (!frame.has("captureTimestamp") || !frame.get("captureTimestamp").isNull() ||
                !frame.path("mirrored").isBoolean() || !SCORES.equals(text(frame, "scoreSemantics"))) fail();
            int rotation = integer(frame.get("rotationDegrees"), 0, 270);
            if (!Set.of(0, 90, 180, 270).contains(rotation)) fail();
            integer(frame.get("imageWidth"), 1, 8192);
            integer(frame.get("imageHeight"), 1, 8192);
            JsonNode points = frame.get("keypoints"), scores = frame.get("scores"), mask = frame.get("validity");
            if (points == null || scores == null || mask == null || !points.isArray() ||
                !scores.isArray() || !mask.isArray() || points.size() != 17 ||
                scores.size() != 17 || mask.size() != 17) fail();
            int valid = 0;
            for (int index = 0; index < 17; index++) {
                JsonNode point = points.get(index), score = scores.get(index), flag = mask.get(index);
                if (!point.isNull() && (!point.isArray() || point.size() != 2 ||
                    !finite(point.get(0), 0, 1) || !finite(point.get(1), 0, 1))) fail();
                if (!score.isNull() && !finite(score, 0, 1e6)) fail();
                boolean expected = !point.isNull() && !score.isNull() && score.asDouble() >= .3;
                if (!flag.isBoolean() || flag.asBoolean() != expected) fail();
                if (expected) valid++;
            }
            if (valid >= 8) validFrames++;
        }
        double duration = (previousTime - first) / 1000.0;
        if (duration <= 0 || duration > 20 || nonnegative(input.get("frameId")) != previousId ||
            !close(input.get("duration"), duration)) fail();
        only(input.get("trackingQuality"), Set.of("validFrameRatio"));
        if (!close(input.path("trackingQuality").get("validFrameRatio"),
            (double) validFrames / frames.size())) fail();
        ObjectNode safe = (ObjectNode) canonical(input);
        safe.remove("subjectId");
        return new ResearchSampleValidator.ValidatedSample(text(input, "sampleId"), side,
            text(input, "cameraView"), captured, safe);
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> keys = new ArrayList<>(); node.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys); keys.forEach(key -> result.set(key, canonical(node.get(key))));
            return result;
        }
        if (node.isArray()) { var result = mapper.createArrayNode(); node.forEach(v -> result.add(canonical(v))); return result; }
        return node.deepCopy();
    }
    private static boolean emptyArray(JsonNode n) { return n != null && n.isArray() && n.isEmpty(); }
    private static void only(JsonNode n, Set<String> allowed) {
        if (n == null || !n.isObject()) fail();
        n.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) fail(); });
    }
    private static String text(JsonNode n, String key) {
        JsonNode value = n.get(key); return value != null && value.isTextual() ? value.asText() : "";
    }
    private static void choice(JsonNode n, String key, String... options) {
        if (!Set.of(options).contains(text(n, key))) fail();
    }
    private static int integer(JsonNode n, int min, int max) {
        if (n == null || !n.isIntegralNumber() || !n.canConvertToInt() || n.asInt() < min || n.asInt() > max) fail();
        return n.asInt();
    }
    private static long nonnegative(JsonNode n) {
        if (n == null || !n.isIntegralNumber() || !n.canConvertToLong() || n.asLong() < 0) fail();
        return n.asLong();
    }
    private static boolean finite(JsonNode n, double min, double max) {
        return n != null && n.isNumber() && Double.isFinite(n.asDouble()) && n.asDouble() >= min && n.asDouble() <= max;
    }
    private static boolean close(JsonNode n, double value) {
        return finite(n, -1e9, 1e9) && Math.abs(n.asDouble() - value) <= 1e-5;
    }
    private static void fail() { throw invalid(); }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY_REVIEW_SAMPLE");
    }
}
