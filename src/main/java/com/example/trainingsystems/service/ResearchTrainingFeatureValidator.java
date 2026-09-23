package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** Mirrors ml/feature_schema.py for export eligibility; not a model or clinical score. */
@Component
public class ResearchTrainingFeatureValidator {
    public boolean matches(JsonNode sample) {
        try {
            JsonNode frames = sample.path("frames");
            JsonNode features = sample.path("features");
            if (!frames.isArray() || frames.size() < 4 || !features.isArray() || features.size() != 5) return false;
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
            double[] expected = {height, hip, knee, lean, duration};
            for (int i = 0; i < expected.length; i++) {
                if (!Double.isFinite(expected[i]) ||
                    Math.abs(features.get(i).asDouble() - expected[i]) > 1e-5) return false;
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private double value(JsonNode points, int index, int axis) {
        double result = points.get(index).get(axis).asDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException();
        return result;
    }

    private double angle(JsonNode points, int a, int center, int b) {
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
