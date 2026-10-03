package com.example.trainingsystems.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class ResearchHandContractTest {
    static final ObjectMapper MAPPER=new ObjectMapper();
    static ObjectNode fixture(String action) {
        var d=ResearchActionRegistry.PRODUCTION.byId(action);
        ObjectNode s=MAPPER.createObjectNode();
        s.put("schemaVersion",2).put("actionId",action).put("actionDefinitionVersion",d.version())
            .put("sampleId","synthetic-hand").put("subjectId","synthetic-group")
            .put("movementSide","unknown").put("anatomicalSide","unknown").put("cameraView","front")
            .put("capturedAt",Instant.now().toString()).put("landmarkSource","mediapipe_hand_21")
            .put("extractorVersion","hand-image-proxy-v1").put("modelInputVersion","hand-features-v1")
            .put("timestampOrigin","channel-arrival-stopwatch").put("timestampMs",1500);
        s.set("featureNames",MAPPER.valueToTree(d.featureNames()));
        s.set("orderedFeatureNames",MAPPER.valueToTree(d.featureNames()));
        s.putObject("segment").put("startMs",0).put("endMs",1500).put("kind","rule-rep-boundaries").put("completedReps",1);
        // Same synthetic recipe + independently fixed expected values as g5_hand_motion.json.
        double[] angles={0,30,60,30,0,-30},pinch={0.4,0.2,0.1,0.3,0.4,0.2};
        var frames=s.putArray("frames");
        for(int i=0;i<angles.length;i++) {
            var f=frames.addObject(); f.put("timestampMs",i*300); var p=f.putArray("landmarks");
            double angle=Math.toRadians(angles[i]);
            for(int j=0;j<21;j++) {
                double x=0.1,y=0;
                if(j==0) {x=0;y=0;}
                if(j==9) x=0.2;
                if(j==5) {x=0.15;y=-0.05;}
                if(j==17) {x=0.15;y=0.05;}
                if(j==6) {x=0.15;y=-0.02;}
                if(j==4) {x=0.15+pinch[i]*0.2;y=-0.02;}
                p.addArray().add(0.5+x*Math.cos(angle)-y*Math.sin(angle)).add(0.5+x*Math.sin(angle)+y*Math.cos(angle)).add(0);
            }
        }
        double[] expected=action.equals("turnPalm")?new double[]{0.5,0,90,30,1.5}:
            action.equals("sidePinch")?new double[]{0.1,0.4,0.3,0,1.5}:new double[]{-30,60,90,30,1.5};
        s.set("features",MAPPER.valueToTree(expected)); return s;
    }
    @Test void allFourGoldenFeaturesCanonicalRetryAndLegacyStaySeparate() {
        var validator=new ResearchSampleValidator(MAPPER);
        var training=new ResearchTrainingFeatureValidator();
        for(var def:ResearchActionRegistry.HANDS) {
            var s=fixture(def.actionId()); var safe=validator.validate(s).safePayload();
            assertThat(training.matches(safe)).isTrue();
            assertThat(safe.has("subjectId")).isFalse();
            assertThat(validator.validate(s).safePayload()).isEqualTo(safe);
            assertThat(safe.path("frames").get(0).has("confidence")).isFalse();
            assertThat(validator.validateStored(safe).side()).isEqualTo("unknown");
            s.put("schemaVersion",1);
            assertThatThrownBy(()->validator.validate(s)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }
    @Test void noFakeConfidenceInvalidGeometryOrUnboundedPrivatePayload() {
        var validator=new ResearchSampleValidator(MAPPER);
        for(int bad=0;bad<8;bad++) {
            var s=fixture("turnPalm");
            switch(bad) {
                case 0 -> ((ObjectNode)s.path("frames").get(0)).putArray("confidence").add(1);
                case 1 -> s.put("email","do-not-export@example.invalid");
                case 2 -> s.put("anatomicalSide","left");
                case 3 -> s.put("actionDefinitionVersion","wrong");
                case 4 -> ((ObjectNode)s.path("frames").get(1)).put("timestampMs",800);
                case 5 -> s.putArray("features").add(99).add(99).add(99).add(99).add(99);
                case 6 -> s.put("landmarkSource","rtmpose");
                case 7 -> s.put("orderedFeatureNames","wrong");
            }
            assertThatThrownBy(()->validator.validate(s)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }
}
