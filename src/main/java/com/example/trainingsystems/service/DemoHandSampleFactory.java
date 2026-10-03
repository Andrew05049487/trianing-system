package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Set;

/** Deterministic drawing geometry, NOT captured human data or a clinical model. */
public final class DemoHandSampleFactory {
    public static final Set<String> IDS = Set.of("DEMO-HAND-001", "DEMO-HAND-002", "DEMO-HAND-003");
    private DemoHandSampleFactory() {}

    public static ObjectNode create(ObjectMapper mapper, String id, Instant capturedAt) {
        if (!IDS.contains(id)) throw new IllegalArgumentException("Unknown demo fixture");
        var definition = ResearchActionRegistry.PRODUCTION.byId("sidePinch");
        ObjectNode sample = mapper.createObjectNode();
        sample.put("sampleId", id).put("subjectId", "DEV-SUBJECT-001")
            .put("schemaVersion", 2).put("actionId", "sidePinch")
            .put("actionDefinitionVersion", definition.version())
            .put("landmarkSource", "mediapipe_hand_21")
            .put("extractorVersion", "hand-image-proxy-v1")
            .put("modelInputVersion", "hand-features-v1")
            .put("timestampOrigin", "channel-arrival-stopwatch")
            .put("anatomicalSide", "unknown").put("movementSide", "unknown")
            .put("cameraView", "front").put("capturedAt", capturedAt.toString())
            .put("timestampMs", 4000);
        sample.set("featureNames", mapper.valueToTree(definition.featureNames()));
        sample.set("orderedFeatureNames", mapper.valueToTree(definition.featureNames()));
        sample.putObject("segment").put("startMs", 0).put("endMs", 4000)
            .put("kind", "rule-rep-boundaries").put("completedReps", 1);
        var frames = sample.putArray("frames");
        // Wrist, thumb and four fingers in MediaPipe's anatomical index order.
        double[][] open = {{.50,.83,0},{.39,.74,0},{.32,.64,0},{.26,.56,0},{.20,.50,0},
            {.40,.57,0},{.38,.43,0},{.37,.32,0},{.36,.23,0},
            {.50,.55,0},{.50,.39,0},{.50,.27,0},{.50,.18,0},
            {.59,.58,0},{.61,.43,0},{.62,.33,0},{.63,.25,0},
            {.67,.63,0},{.73,.51,0},{.76,.43,0},{.79,.36,0}};
        for (int f = 0; f <= 40; f++) {
            double pinch = (1 - Math.cos(2 * Math.PI * f / 40)) / 2;
            var points = frames.addObject().put("timestampMs", f * 100).putArray("landmarks");
            for (int p = 0; p < 21; p++) {
                double x = open[p][0], y = open[p][1];
                if (p >= 1 && p <= 4) {
                    double influence = p / 4.0;
                    x += (.38 - open[4][0]) * influence * pinch;
                    y += (.45 - open[4][1]) * influence * pinch;
                }
                points.addArray().add(x).add(y).add(0);
            }
        }
        sample.set("features", mapper.valueToTree(ResearchHandFeatures.extract(sample)));
        new ResearchSampleValidator(mapper).validate(sample);
        return sample;
    }
}
