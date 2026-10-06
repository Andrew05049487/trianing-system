package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ResearchBodyAttemptTest {
    @Test void sameCrossLanguageFixtureValidates() throws Exception {
        try(var stream=getClass().getResourceAsStream("/body_attempt_v3_synthetic.json")) {
            assertThat(stream).isNotNull();
            var result=new ResearchSampleValidator(M).validateStored(M.readTree(stream));
            assertThat(result.safePayload().path("schemaVersion").asInt()).isEqualTo(3);
        }
    }
    static final ObjectMapper M=new ObjectMapper();
    static ObjectNode fixture(){
        ObjectNode n=M.createObjectNode();
        n.put("schemaVersion",3);n.put("modality","body");n.put("actionId","standing_knee_raise");
        n.put("actionDefinitionVersion",ResearchBodyAttemptValidator.DEFINITION);
        n.put("extractorVersion",ResearchBodyAttemptValidator.EXTRACTOR);n.put("modelInputVersion",ResearchBodyAttemptValidator.INPUT);
        n.put("sampleId","synthetic-body");n.put("sessionId","session-1");n.put("attemptId","attempt-1");
        n.put("exerciseId","99");n.put("exerciseType","DEFAULT");n.put("source","tv_pi");n.put("platform","android_tv");
        n.put("streamSessionId","stream-1");n.put("frameId",3);n.put("timestampOrigin","tv_receive_monotonic");
        n.put("poseModelVersion","rtmpose-wholebody-133-v1");n.put("coordinateTransformVersion","rtmpose-image-normalized-v1");
        n.put("movementSide","left");n.put("cameraView","rear");n.put("capturedAt",Instant.now().toString());
        n.put("featuresStatus","available");n.put("duration",.6);n.put("terminationReason","RETURNED_TO_BASELINE");
        n.put("setIndex",1);n.put("completedRepsBefore",0);n.put("completedRepsAfter",0);n.put("intendedRepetition",1);
        n.putObject("trackingQuality").put("validFrameRatio",1.0);
        n.set("featureNames",M.valueToTree(ResearchActionRegistry.STANDING_DEFINITION.featureNames()));
        var features=n.putArray("features");for(double v:new double[]{0,180,180,0,.6}) features.add(v);
        var frames=n.putArray("frames");
        for(int t=0;t<4;t++){
            var f=frames.addObject();f.put("frameId",t);f.put("timestampMs",t*200);
            for(String k:List.of("streamSessionId","timestampOrigin","source","poseModelVersion","coordinateTransformVersion")) f.set(k,n.get(k));
            f.putNull("captureTimestamp");f.put("imageWidth",640);f.put("imageHeight",480);f.put("mirrored",false);f.put("rotationDegrees",0);
            f.put("scoreSemantics",ResearchBodyAttemptValidator.SCORES);
            var points=f.putArray("keypoints");var scores=f.putArray("scores");var mask=f.putArray("validity");
            for(int i=0;i<17;i++){
                double x=(i==6 || i==12 || i==14 || i==16)?.6:.4;
                double y=(i==5 || i==6)?.2:(i==11 || i==12)?.5:(i==13 || i==14)?.7:(i==15 || i==16)?.9:.5;
                points.addArray().add(x).add(y);scores.add(1.2);mask.add(true);
            }
            f.putObject("angles").put("legHeight",0).put("hipDeg",180).put("kneeDeg",180).put("trunkLeanDeg",0);
        }
        return n;
    }
    @Test void validUncountedBodyAcceptedWithoutChangingV1OrHandV2(){
        var validator=new ResearchSampleValidator(M);var sample=validator.validate(fixture());
        assertThat(sample.safePayload().path("completedRepsAfter").asInt()).isZero();
        assertThat(validator.definition(sample.safePayload()).schemaVersion()).isEqualTo(3);
        assertThat(validator.action("standing_knee_raise").schemaVersion()).isEqualTo(1);
        assertThat(validator.validate(ResearchHandContractTest.fixture("sidePinch")).safePayload().path("schemaVersion").asInt()).isEqualTo(2);
    }
    @Test void incorrectFeaturesAndInvalidMetadataRejected(){
        for(String key:List.of("modality","extractorVersion","timestampOrigin","actionId")){
            var p=fixture();p.put(key,"invalid");reject(p);
        }
        var p=fixture();p.put("patientId",123);reject(p);
        p=fixture();((ArrayNode)p.get("features")).set(0,DoubleNode.valueOf(.8));reject(p);
        p=fixture();((ObjectNode)p.path("frames").get(0)).put("captureTimestamp","2026-01-01T00:00:00Z");reject(p);
        p=fixture();((ArrayNode)p.path("frames").get(0).path("validity")).set(5,BooleanNode.FALSE);reject(p);
    }
    @Test void missingTrackingFeaturesAreNullNotFakeZero(){
        var p=fixture();var f=(ObjectNode)p.path("frames").get(3);
        ((ArrayNode)f.path("scores")).set(5,DoubleNode.valueOf(.1));
        ((ArrayNode)f.path("validity")).set(5,BooleanNode.FALSE);f.putNull("angles");
        p.put("featuresStatus","unavailable");p.putObject("trackingQuality").put("validFrameRatio",.75);
        var features=p.putArray("features");for(int i=0;i<5;i++)features.addNull();
        assertThat(new ResearchSampleValidator(M).validate(p).safePayload().path("featuresStatus").asText()).isEqualTo("unavailable");
        features.set(0,DoubleNode.valueOf(0));reject(p);
    }
    @Test void fieldOrderCanonicalAndSubjectNeverTrusted(){
        var a=fixture();a.put("subjectId","untrusted");
        var b=M.createObjectNode();List<String> keys=new ArrayList<>();a.fieldNames().forEachRemaining(keys::add);
        Collections.reverse(keys);keys.forEach(k->b.set(k,a.get(k)));
        var validator=new ResearchSampleValidator(M);
        assertThat(validator.validate(a).safePayload()).isEqualTo(validator.validate(b).safePayload());
        assertThat(validator.validate(a).safePayload().has("subjectId")).isFalse();
    }
    @Test void assignmentMustBeActiveCorrectActionAndBound(){
        var assignments=mock(ExerciseAssignmentRepository.class);var bindings=mock(UserBindingRepository.class);
        var gate=new ResearchBodyAssignmentService(assignments,bindings);
        assertThatThrownBy(()->gate.requireAssigned(1L,fixture())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        var a=new ExerciseAssignmentEntity();var e=new Exercise();e.setId(99L);e.setExerciseName("站姿抬腳式訓練");a.setExercise(e);
        var therapist=new User();therapist.setId(2L);a.setAssignedByTherapist(therapist);
        when(assignments.findByExercise_IdAndPatient_Id(99L,1L)).thenReturn(Optional.of(a));
        assertThatThrownBy(()->gate.requireAssigned(1L,fixture())).hasMessageContaining("403");
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L,2L,"THERAPIST")).thenReturn(true);
        gate.requireAssigned(1L,fixture());a.setActive(false);
        assertThatThrownBy(()->gate.requireAssigned(1L,fixture())).hasMessageContaining("403");
    }
    @Test void ownerSampleAndAttemptRetryAreIdempotentAndConflictingPayload409(){
        var users=mock(UserRepository.class);var identity=mock(CustomExerciseIdentityService.class);
        var consents=mock(ResearchConsentRepository.class);var samples=mock(ResearchSampleRepository.class);
        var retention=mock(ResearchRetentionService.class);var assignments=mock(ResearchBodyAssignmentService.class);
        var service=new ResearchDataService(users,mock(UserBindingRepository.class),identity,consents,samples,
            mock(ResearchAnnotationRepository.class),mock(ResearchAnnotationRevisionRepository.class),mock(ResearchAuditRepository.class),
            new ResearchSampleValidator(M),mock(ResearchAuthorityService.class),retention,M,true,"test-v1");
        ReflectionTestUtils.setField(service,"bodyAssignments",assignments);
        var user=new User();user.setId(1L);user.setRole("PATIENT");when(users.findById(1L)).thenReturn(Optional.of(user));
        when(identity.isConfigured()).thenReturn(true);when(identity.isValid(user,"test-token")).thenReturn(true);
        var consent=new ResearchConsentEntity();consent.setActive(true);consent.setConsentVersion("test-v1");consent.setSubjectId("server-subject");
        when(consents.findById(1L)).thenReturn(Optional.of(consent));
        var policy=new ResearchRetentionPolicyEntity();policy.setPolicyVersion("test-policy");policy.setRetentionDays(1);when(retention.currentPolicy()).thenReturn(Optional.of(policy));
        var saved=new ArrayList<ResearchSampleEntity>();when(samples.saveAndFlush(any())).thenAnswer(i->{saved.add(i.getArgument(0));return i.getArgument(0);});
        var p=fixture();var first=service.upload(1L,"test-token",p);
        when(samples.findByParticipantUserIdAndAttemptId(1L,"attempt-1")).thenReturn(Optional.of(saved.get(0)));
        assertThat(service.upload(1L,"test-token",p).id()).isEqualTo(first.id());
        assertThat(saved).hasSize(1);assertThat(first.modality()).isEqualTo("body");
        p.put("sampleId","new-client-id");
        assertThatThrownBy(()->service.upload(1L,"test-token",p)).hasMessageContaining("409");
        verify(assignments,times(3)).requireAssigned(eq(1L),any());
    }
    private void reject(JsonNode p){assertThatThrownBy(()->new ResearchSampleValidator(M).validate(p)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");}
}
