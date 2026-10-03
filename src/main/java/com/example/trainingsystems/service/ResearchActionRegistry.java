package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit action contracts; neither registration nor a label grants research authority. */
public final class ResearchActionRegistry {
    public record AngleRange(double min, double max) {}
    public record Definition(String actionId, int schemaVersion, String version,
        List<String> featureNames, Map<String, AngleRange> angles,
        Set<Integer> leftRequired, Set<Integer> rightRequired,
        int durationFeature, Set<String> labels, Set<String> trainableLabels,
        boolean acceptLegacyVersion, Function<JsonNode, double[]> extractor) {
        public Definition {
            featureNames = List.copyOf(featureNames);
            angles = Map.copyOf(angles);
            leftRequired = Set.copyOf(leftRequired);
            rightRequired = Set.copyOf(rightRequired);
            labels = Set.copyOf(labels);
            trainableLabels = Set.copyOf(trainableLabels);
        }
        public boolean acceptsVersion(JsonNode sample) {
            return sample.path("schemaVersion").asInt(-1) == schemaVersion &&
                (version.equals(sample.path("actionDefinitionVersion").asText()) ||
                 (acceptLegacyVersion && !sample.has("actionDefinitionVersion")));
        }
    }

    public static final String STANDING = "standing_knee_raise";
    public static final Definition STANDING_DEFINITION = new Definition(STANDING, 1,
        "standing-knee-raise-v1",
        List.of("peak_leg_height", "minimum_hip_angle_deg", "minimum_knee_angle_deg",
            "peak_abs_trunk_lean_deg", "duration_seconds"),
        Map.of("hipDeg", new AngleRange(0, 180), "kneeDeg", new AngleRange(0, 180),
            "trunkLeanDeg", new AngleRange(-180, 180)),
        Set.of(5, 6, 11, 12, 13, 15), Set.of(5, 6, 11, 12, 14, 16), 4,
        Set.of("meets_requirement", "insufficient_range", "trunk_compensation", "unassessable"),
        Set.of("meets_requirement", "insufficient_range", "trunk_compensation"), true,
        ResearchTrainingFeatureValidator::standingFeatures);
    // Never add synthetic tests or arbitrary CUSTOM exercises here.
    public static final ResearchActionRegistry PRODUCTION =
        new ResearchActionRegistry(List.of(STANDING_DEFINITION));

    private final Map<String, Definition> definitions;
    public ResearchActionRegistry(List<Definition> definitions) {
        this.definitions = Map.copyOf(definitions.stream().collect(
            Collectors.toMap(Definition::actionId, Function.identity())));
    }
    public Definition byId(String id) { return definitions.get(id); }
    public Definition forSample(JsonNode sample) {
        if (sample == null) return null;
        Definition definition = byId(sample.path("actionId").asText());
        return definition != null && definition.acceptsVersion(sample) ? definition : null;
    }
}
