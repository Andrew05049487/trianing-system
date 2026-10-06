package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Local isolated MySQL only. Every fixture and policy rolls back, not a study approval. */
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false",
    "research.collection-enabled=false","research.consent-version=round3-synthetic-v1",
    "research.hand-consent-version=round3-synthetic-v1"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="DB_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:3306/rehab_body_r3_validation\\?.*")
class BodyRound3MySqlIntegrationTest {
    @TestConfiguration static class SyntheticOnly {
        @Bean @Primary ResearchDataService round3SyntheticResearch(UserRepository u,UserBindingRepository b,
            CustomExerciseIdentityService i,ResearchConsentRepository c,ResearchSampleRepository s,
            ResearchAnnotationRepository a,ResearchAnnotationRevisionRepository r,ResearchAuditRepository audit,
            ResearchSampleValidator v,ResearchAuthorityService auth,ResearchRetentionService retention,ObjectMapper m) {
            return new ResearchDataService(u,b,i,c,s,a,r,audit,v,auth,retention,m,true,"round3-synthetic-v1");
        }
    }
    @Autowired JdbcTemplate jdbc; @Autowired EntityManager entities; @Autowired ObjectMapper mapper;
    @Autowired UserRepository users; @Autowired UserBindingRepository bindings;
    @Autowired ResearchGrantRepository grants; @Autowired ResearchRetentionPolicyRepository policies;
    @Autowired ResearchConsentRepository consents; @Autowired ResearchSampleRepository samples;
    @Autowired ExerciseRepository exercises; @Autowired ExerciseAssignmentRepository assignments;
    @Autowired ResearchDataService research; @Autowired ResearchManagementService management;
    @Autowired ResearchAuthorityService authority; @Autowired CustomExerciseIdentityService identity;
    @Autowired ResearchAnnotationRepository annotations; @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired @Qualifier("researchDataService") ResearchDataService productionResearch;
    User patient,author,reviewer,manager; Exercise exercise;
    @BeforeEach void fixtures() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("rehab_body_r3_validation");
        patient=user("PATIENT");author=user("THERAPIST");reviewer=user("THERAPIST");manager=user("THERAPIST");
        for(User therapist:new User[]{author,reviewer}) {var b=new UserBinding();b.setPatient(patient);b.setLinkedUser(therapist);b.setRelationship("THERAPIST");bindings.saveAndFlush(b);}
        grant(author,true,false,false);grant(reviewer,true,true,false);grant(manager,false,false,true);
        var policy=new ResearchRetentionPolicyEntity();policy.setStudyId(ResearchAuthorityService.STUDY_ID);
        policy.setPolicyVersion("fake-"+UUID.randomUUID());policy.setRetentionDays(1);
        policy.setEffectiveAt(Instant.now().minusSeconds(1));policy.setConfiguredByUserId(manager.getId());
        policy.setApprovalReference("SYNTHETIC-TEST-NOT-APPROVAL");policy.setCreatedAt(Instant.now());policies.saveAndFlush(policy);
        exercise=new Exercise();exercise.setExerciseName("站姿抬腳式訓練");exercise=exercises.saveAndFlush(exercise);
        var assignment=new ExerciseAssignmentEntity();assignment.setExercise(exercise);assignment.setPatient(patient);assignment.setAssignedByTherapist(author);assignments.saveAndFlush(assignment);
        research.setConsent(patient.getId(),token(patient),true,"round3-synthetic-v1");
    }
    @Test void validateMetadataAndProductionFlagRemainsClosed() {
        assertThat(entities.getMetamodel().getEntities()).hasSize(29);
        assertThat(jdbc.queryForObject("SELECT VERSION()",String.class)).isEqualTo("8.4.11");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()",Integer.class)).isEqualTo(257);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()",Integer.class)).isEqualTo(34);
        assertThat(productionResearch.consent(patient.getId(),token(patient)).available()).isFalse();
    }
    @Test void uploadIdempotencyConflictAndAttemptUnique() {
        var payload=body();var one=research.upload(patient.getId(),token(patient),payload);
        assertThat(research.upload(patient.getId(),token(patient),payload).id()).isEqualTo(one.id());
        var different=payload.deepCopy();different.put("cameraView","front");
        assertThatThrownBy(()->research.upload(patient.getId(),token(patient),different)).hasMessageContaining("409");
        var duplicateAttempt=payload.deepCopy();duplicateAttempt.put("sampleId",UUID.randomUUID().toString());
        assertThatThrownBy(()->research.upload(patient.getId(),token(patient),duplicateAttempt)).hasMessageContaining("409");
        assertThat(samples.findByParticipantUserId(patient.getId())).hasSize(1);
    }
    @Test void v1BodyAndV2HandPersistWithNullableV3Metadata() {
        var v1=legacyBody();var b=research.upload(patient.getId(),token(patient),v1);
        var v2=ResearchHandContractTest.fixture("sidePinch");v2.put("sampleId",UUID.randomUUID().toString());
        var h=research.upload(patient.getId(),token(patient),v2);
        entities.flush();entities.clear();
        assertThat(research.detail(patient.getId(),token(patient),b.id()).payload().path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(research.detail(patient.getId(),token(patient),h.id()).payload().path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(samples.findById(b.id()).orElseThrow().getSource()).isNull();
        assertThat(samples.findById(h.id()).orElseThrow().getAttemptId()).isNull();
    }
    @Test void apiUploadIndependentReviewAndSourceSpecificExport() throws Exception {
        var response=mvc.perform(post("/api/ml-research/samples").header("X-User-Id",patient.getId())
            .header("X-Custom-Exercise-Token",token(patient)).contentType("application/json").content(body().toString()))
            .andExpect(status().isOk()).andReturn().getResponse();
        String id=mapper.readTree(response.getContentAsString()).path("id").asText();
        lifecycle(id,"APPROVE");entities.flush();
        assertThat(entry(management.exportApproved(manager.getId(),token(manager),"standing_knee_raise",3,"tv_pi"),"manifest.json")).contains("\"sampleCount\":1","sessionGrouping");
        assertThat(entry(management.exportApproved(manager.getId(),token(manager),"standing_knee_raise",3,"phone"),"manifest.json")).contains("\"sampleCount\":0");
    }
    @Test void needsResampleIsImmutableAndLinkedNewSampleGetsNewReview() throws Exception {
        var parent=research.upload(patient.getId(),token(patient),body());lifecycle(parent.id(),"NEEDS_RESAMPLE");
        String original=samples.findById(parent.id()).orElseThrow().getPayloadJson();
        var next=body();next.put("resampleOfSampleId",parent.id());var child=research.upload(patient.getId(),token(patient),next);
        assertThat(child.resampleOfSampleId()).isEqualTo(parent.id());assertThat(child.id()).isNotEqualTo(parent.id());
        lifecycle(child.id(),"APPROVE");entities.flush();
        assertThat(samples.findById(parent.id()).orElseThrow().getPayloadJson()).isEqualTo(original);
        assertThat(samples.findById(parent.id()).orElseThrow().getDisposition()).isEqualTo("NEEDS_RESAMPLE");
        assertThat(entry(management.exportApproved(manager.getId(),token(manager),"standing_knee_raise",3,"tv_pi"),"manifest.json")).contains("\"sampleCount\":1");
    }
    @Test void revokedConsentExcludedAndUnauthorizedAccessRejected() throws Exception {
        var sample=research.upload(patient.getId(),token(patient),body());lifecycle(sample.id(),"APPROVE");
        var other=user("THERAPIST");
        assertThatThrownBy(()->research.detail(other.getId(),token(other),sample.id())).hasMessageContaining("403");
        research.setConsent(patient.getId(),token(patient),false,"round3-synthetic-v1");entities.flush();
        assertThat(entry(management.exportApproved(manager.getId(),token(manager),"standing_knee_raise",3,"tv_pi"),"manifest.json")).contains("\"sampleCount\":0");
    }
    @Test void concurrentRetryAndFirstDraftAreSerialized() throws Exception {
        // Only this test commits ephemeral fixtures, then cleans exact generated IDs.
        var payload=body();TestTransaction.flagForCommit();TestTransaction.end();
        var pool=Executors.newFixedThreadPool(2);
        try {
            var start=new CountDownLatch(1);
            Callable<String> upload=()->{if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError("start timeout");
                return research.upload(patient.getId(),token(patient),payload.deepCopy()).id();};
            var one=pool.submit(upload);var two=pool.submit(upload);start.countDown();
            String id=one.get(15,TimeUnit.SECONDS);assertThat(two.get(15,TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(samples.findByParticipantUserId(patient.getId())).hasSize(1);
            var editStart=new CountDownLatch(1);
            Callable<String> edit=()->{if(!editStart.await(5,TimeUnit.SECONDS))throw new AssertionError("edit timeout");
                try {research.label(author.getId(),token(author),id,"insufficient_range","fake note","body-attempt-label-v1",ResearchBodyAttemptValidator.DEFINITION,0);return "saved";}
                catch(org.springframework.web.server.ResponseStatusException conflict) {assertThat(conflict.getStatusCode().value()).isEqualTo(409);return "conflict";}};
            var first=pool.submit(edit);var second=pool.submit(edit);editStart.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","conflict");
        } finally {
            pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("rehab_body_r3_validation");
                jdbc.update("DELETE r FROM research_annotation_revisions r JOIN research_samples s ON r.sample_id=s.id WHERE s.participant_user_id=?",patient.getId());
                jdbc.update("DELETE a FROM research_annotations a JOIN research_samples s ON a.sample_id=s.id WHERE s.participant_user_id=?",patient.getId());
                jdbc.update("DELETE FROM research_samples WHERE participant_user_id=?",patient.getId());
                jdbc.update("DELETE FROM research_consents WHERE user_id=?",patient.getId());
                for(User u:new User[]{patient,author,reviewer,manager}) {
                    jdbc.update("DELETE FROM research_audit WHERE actor_user_id=?",u.getId());
                    jdbc.update("DELETE FROM research_export_audit WHERE actor_user_id=?",u.getId());
                    jdbc.update("DELETE FROM research_grants WHERE user_id=?",u.getId());
                }
                jdbc.update("DELETE FROM research_retention_policies WHERE configured_by_user_id=?",manager.getId());
                jdbc.update("DELETE FROM exercise_assignments WHERE patient_id=?",patient.getId());
                jdbc.update("DELETE FROM user_bindings WHERE patient_id=?",patient.getId());
                for(User u:new User[]{patient,author,reviewer,manager})jdbc.update("DELETE FROM users WHERE id=?",u.getId());
                jdbc.update("DELETE FROM exercise WHERE id=?",exercise.getId());
            });
        }
    }
    void lifecycle(String id,String decision) {
        research.label(author.getId(),token(author),id,"insufficient_range","synthetic note","body-attempt-label-v1",ResearchBodyAttemptValidator.DEFINITION,0);
        research.submitLabel(author.getId(),token(author),id,1);
        research.reviewLabel(reviewer.getId(),token(reviewer),id,decision,"synthetic reason","LOW_QUALITY",2);
    }
    ObjectNode body() {
        var n=ResearchBodyAttemptTest.fixture();n.put("sampleId",UUID.randomUUID().toString());n.put("attemptId",UUID.randomUUID().toString());
        n.put("sessionId",UUID.randomUUID().toString());n.put("exerciseId",exercise.getId().toString());return n;
    }
    ObjectNode legacyBody() {
        var n=mapper.createObjectNode();n.put("schemaVersion",1);n.put("sampleId",UUID.randomUUID().toString());n.put("actionId","standing_knee_raise");
        n.put("movementSide","left");n.put("cameraView","front");n.put("capturedAt",Instant.now().toString());
        n.putObject("segment").put("startMs",0).put("endMs",300);
        n.set("featureNames",mapper.valueToTree(ResearchActionRegistry.STANDING_DEFINITION.featureNames()));
        var values=n.putArray("features");for(double v:new double[]{-1,180,180,0,.3})values.add(v);
        var frames=n.putArray("frames");for(int t=0;t<=300;t+=100) {
            var f=frames.addObject();f.put("timestampMs",t);var points=f.putArray("landmarks");var conf=f.putArray("confidence");
            for(int i=0;i<17;i++) {double x=(i==6||i==12||i==14||i==16)?2:0;
                double y=(i==11||i==12)?1:(i==13||i==14)?2:(i==15||i==16)?3:0;
                points.addArray().add(x).add(y);conf.add(.9);}
            f.putObject("angles").put("hipDeg",180).put("kneeDeg",180).put("trunkLeanDeg",0);
        }return n;
    }
    User user(String role) {var u=new User();u.setEmail("round3_"+UUID.randomUUID()+"@example.invalid");u.setName("虛構驗證帳號");u.setRole(role);return users.saveAndFlush(u);}
    String token(User user){return identity.issueToken(user);}
    void grant(User u,boolean a,boolean r,boolean m) {var g=new ResearchGrantEntity();g.setUserId(u.getId());g.setStudyId(ResearchAuthorityService.STUDY_ID);
        g.setCanAnnotate(a);g.setCanReview(r);g.setCanManage(m);g.setUpdatedAt(Instant.now());grants.saveAndFlush(g);}
    String entry(byte[] bytes,String name) throws Exception {try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
        for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals(name))return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
    }throw new AssertionError(name);}
}
