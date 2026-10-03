package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DemoHandSampleFactoryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void completeOpenPinchReturnUsesRealExtractorAndValidator() {
        var sample = DemoHandSampleFactory.create(mapper, "DEMO-HAND-001", Instant.now());
        var valid = new ResearchSampleValidator(mapper).validate(sample);
        assertThat(valid.safePayload().path("frames").size()).isEqualTo(41);
        assertThat(sample.path("frames").get(0).path("landmarks")).hasSize(21);
        assertThat(sample.path("frames").get(40).path("landmarks"))
            .isEqualTo(sample.path("frames").get(0).path("landmarks"));
        assertThat(sample.path("frames").get(20).path("landmarks"))
            .isNotEqualTo(sample.path("frames").get(0).path("landmarks"));
        assertThat(sample.path("features")).isEqualTo(mapper.valueToTree(ResearchHandFeatures.extract(sample)));
        assertThat(sample.path("features").get(2).asDouble()).isGreaterThan(0.1);
        assertThat(sample.path("features").get(4).asDouble()).isEqualTo(4);
    }

    @Test void corruptedFeaturesAreNotAcceptedForDemonstration() {
        var sample = DemoHandSampleFactory.create(mapper, "DEMO-HAND-001", Instant.now());
        sample.putArray("features").add(99).add(99).add(99).add(99).add(99);
        assertThatThrownBy(() -> new ResearchSampleValidator(mapper).validate(sample))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
