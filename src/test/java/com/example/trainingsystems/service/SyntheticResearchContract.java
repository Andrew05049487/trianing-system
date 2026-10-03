package com.example.trainingsystems.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fictional fixture, not a registered clinical action. */
final class SyntheticResearchContract {
    static final ResearchActionRegistry.Definition DEFINITION = new ResearchActionRegistry.Definition(
        "synthetic_test_only", 1, "synthetic-v1", List.of("duration_seconds"), Map.of(),
        Set.of(5, 6), Set.of(5, 6), 0, Set.of("test_match", "unassessable"), Set.of("test_match"),
        false, sample -> {
            var frames = sample.path("frames");
            return new double[]{(frames.get(frames.size() - 1).path("timestampMs").asLong() -
                frames.get(0).path("timestampMs").asLong()) / 1000.0};
        });
    static final ResearchActionRegistry REGISTRY = new ResearchActionRegistry(
        List.of(ResearchActionRegistry.STANDING_DEFINITION, DEFINITION));
}
