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
    private static Definition hand(String id, List<String> features, String limitation) {
        return new Definition(id, 2, id + "-hand-v1", features, Map.of(), Set.of(), Set.of(), 4,
            Set.of("meets_requirement", limitation, "unstable_motion", "unassessable"),
            Set.of("meets_requirement", limitation, "unstable_motion"), false, ResearchHandFeatures::extract);
    }
    public static final List<Definition> HANDS = List.of(
        hand("turnPalm", List.of("axis_x_range", "palm_normal_z_range", "orientation_range_deg", "orientation_step_mean_deg", "duration_seconds"), "limited_rotation_proxy"),
        hand("sidePinch", List.of("minimum_pinch_ratio", "maximum_pinch_ratio", "pinch_range", "wrist_travel_ratio", "duration_seconds"), "limited_pinch_motion"),
        hand("wristExtension", List.of("minimum_relative_axis_deg", "maximum_relative_axis_deg", "axis_range_deg", "axis_step_mean_deg", "duration_seconds"), "limited_wrist_motion"),
        hand("wristSideBend", List.of("minimum_relative_axis_deg", "maximum_relative_axis_deg", "axis_range_deg", "axis_step_mean_deg", "duration_seconds"), "limited_wrist_motion"));
    // Registration does not expand approved study/consent scope.
    public static final ResearchActionRegistry PRODUCTION =
        new ResearchActionRegistry(java.util.stream.Stream.concat(
            java.util.stream.Stream.of(STANDING_DEFINITION), HANDS.stream()).toList());

    private final Map<String, Definition> definitions;
    public ResearchActionRegistry(List<Definition> definitions) {
        this.definitions = Map.copyOf(definitions.stream().collect(
            Collectors.toMap(Definition::actionId, Function.identity())));
    }
    public Definition byId(String id) { return definitions.get(id); }
    // Explicit version selection. v3 is not the existing v1 export/model input.
    public static final Definition BODY_ATTEMPT = new Definition(STANDING,3,
        ResearchBodyAttemptValidator.DEFINITION,STANDING_DEFINITION.featureNames(),
        STANDING_DEFINITION.angles(),STANDING_DEFINITION.leftRequired(),STANDING_DEFINITION.rightRequired(),4,
        STANDING_DEFINITION.labels(),Set.of(),false,ignored -> null);
    public Definition forSample(JsonNode sample) {
        if (sample == null) return null;
        if (sample.path("schemaVersion").asInt(-1)==3 && STANDING.equals(sample.path("actionId").asText())) {
            return BODY_ATTEMPT.acceptsVersion(sample)?BODY_ATTEMPT:null;
        }
        Definition definition = byId(sample.path("actionId").asText());
        return definition != null && definition.acceptsVersion(sample) ? definition : null;
    }
}
