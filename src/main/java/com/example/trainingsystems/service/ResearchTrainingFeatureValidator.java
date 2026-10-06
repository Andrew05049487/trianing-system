package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** Mirrors ml/feature_schema.py for export eligibility; not a model or clinical score. */
@Component
public class ResearchTrainingFeatureValidator {
    private final ResearchActionRegistry actions;
    @Autowired
    public ResearchTrainingFeatureValidator() { this(ResearchActionRegistry.PRODUCTION); }
    public ResearchTrainingFeatureValidator(ResearchActionRegistry actions) { this.actions = actions; }
    public boolean matches(JsonNode sample) {
        try {
            if (sample.path("schemaVersion").asInt(-1)==3) {
                var safe=new ResearchBodyAttemptValidator(new com.fasterxml.jackson.databind.ObjectMapper())
                    .validate(sample,false).safePayload();
                return "available".equals(safe.path("featuresStatus").asText());
            }
            var definition = actions.forSample(sample);
            if (definition == null) return false;
            JsonNode names = sample.path("featureNames");
            JsonNode features = sample.path("features");
            if (!names.isArray() || names.size() != definition.featureNames().size() ||
                !features.isArray() || features.size() != names.size()) return false;
            for (int i = 0; i < names.size(); i++) {
                if (!definition.featureNames().get(i).equals(names.get(i).asText())) return false;
            }
            double[] expected = definition.extractor().apply(sample);
            if (expected.length != features.size()) return false;
            for (int i = 0; i < expected.length; i++) {
                if (!Double.isFinite(expected[i]) || !features.get(i).isNumber() ||
                    !Double.isFinite(features.get(i).asDouble()) ||
                    Math.abs(features.get(i).asDouble() - expected[i]) > 1e-5) return false;
            }
            return true;
        } catch (RuntimeException error) { return false; }
    }

    static double[] standingFeatures(JsonNode sample) {
            JsonNode frames = sample.path("frames");
            if (!frames.isArray() || frames.size() < 4) throw new IllegalArgumentException();
            int[] side = "left".equals(sample.path("movementSide").asText())
                ? new int[]{5, 11, 13, 15} : new int[]{6, 12, 14, 16};
            double height = Double.NEGATIVE_INFINITY;
            double hip = Double.POSITIVE_INFINITY;
            double knee = Double.POSITIVE_INFINITY;
            double lean = Double.NEGATIVE_INFINITY;
            for (JsonNode frame : frames) {
                JsonNode p = frame.path("landmarks");
                height = Math.max(height, value(p, side[1], 1) - value(p, side[2], 1));
                hip = Math.min(hip, angle(p, side[0], side[1], side[2]));
                knee = Math.min(knee, angle(p, side[1], side[2], side[3]));
                double shoulderX = (value(p, 5, 0) + value(p, 6, 0)) / 2;
                double shoulderY = (value(p, 5, 1) + value(p, 6, 1)) / 2;
                double hipX = (value(p, 11, 0) + value(p, 12, 0)) / 2;
                double hipY = (value(p, 11, 1) + value(p, 12, 1)) / 2;
                lean = Math.max(lean, Math.abs(Math.toDegrees(
                    Math.atan2(shoulderX - hipX, hipY - shoulderY))));
            }
            double duration = (frames.get(frames.size() - 1).path("timestampMs").asDouble() -
                frames.get(0).path("timestampMs").asDouble()) / 1000;
            return new double[]{height, hip, knee, lean, duration};
    }

    private static double value(JsonNode points, int index, int axis) {
        double result = points.get(index).get(axis).asDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException();
        return result;
    }

    private static double angle(JsonNode points, int a, int center, int b) {
        double ux = value(points, a, 0) - value(points, center, 0);
        double uy = value(points, a, 1) - value(points, center, 1);
        double vx = value(points, b, 0) - value(points, center, 0);
        double vy = value(points, b, 1) - value(points, center, 1);
        double length = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        if (length < 1e-6) throw new IllegalArgumentException();
        double cosine = Math.max(-1, Math.min(1, (ux * vx + uy * vy) / length));
        return Math.toDegrees(Math.acos(cosine));
    }
}
