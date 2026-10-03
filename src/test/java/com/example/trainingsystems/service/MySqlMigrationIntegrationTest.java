package com.example.trainingsystems.service;

import jakarta.persistence.EntityManager;
import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.dto.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs only against the explicitly supplied local MySQL database; all fixtures roll back. */
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS, properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:3306/rehab_r2_validation\\?.*")
class MySqlMigrationIntegrationTest {
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired PasswordService passwords;
    @Autowired PasswordResetService resets;
    @Autowired PasswordResetCodeHasher resetHasher;
    @Autowired PasswordResetCredentialRepository resetCredentials;
    @Autowired CustomExerciseIdentityService identity;
    @Autowired AccountService accounts;
    @Autowired CustomRehabExerciseService custom;
    @Autowired CustomExerciseAssignmentService customAssignments;
    @Autowired UnifiedExerciseAssignmentService assignments;
    @Autowired TrainingSessionResultService results;
    @Autowired TrainingHistoryRepository histories;
    @Autowired TrainingHistoryVideoService videos;
    @Autowired ChatService chats;
    @Autowired FriendService friends;
    @Autowired UserAvatarService avatars;
    @Autowired MockMvc mvc;
    @Autowired ResearchDataService productionResearch;
    @Autowired ResearchAuthorityService authority;
    @Autowired ResearchRetentionService retention;
    @Autowired ResearchManagementService management;
    @Autowired UserBindingRepository bindings;
    @Autowired ResearchConsentRepository consents;
    @Autowired ResearchSampleRepository samples;
    @Autowired ResearchAnnotationRepository annotations;
    @Autowired ResearchAnnotationRevisionRepository revisions;
    @Autowired ResearchAuditRepository audits;
    @Autowired ResearchExportAuditRepository exportAudits;
    @Autowired ResearchGrantRepository grants;
    @Autowired RehabPlanService plans;
    @Autowired TherapistRegistrationService therapistRegistration;
    @Autowired TherapistPatientBindingService therapistBindings;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ResearchSampleValidator validator;
    @MockBean GoogleIdentityVerifier google;
    @MockBean PasswordResetEmailService mail;

    @Test
    void validatesAllEntitiesAgainstExistingMySqlSchema() {
        assertThat(entities.getMetamodel().getEntities()).hasSize(29);
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.11");
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("rehab_r2_validation");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class)).isEqualTo(29);
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isIn("+00:00", "UTC");
        assertThat(identity.isConfigured()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()", Integer.class)).isEqualTo(241);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()", Integer.class)).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema=DATABASE()", Integer.class)).isEqualTo(16);
    }

    @Test void registrationLoginAndAccountIdKeepExistingContract() throws Exception {
        var request = mapper.readValue("{\"email\":\"" + unique() + "@example.invalid\",\"password\":\"Fixture-password\",\"name\":\"測試病患🙂\"}", RegisterRequest.class);
        User patient = auth.register(request);
        assertThat(patient.getPassword()).startsWith("$2");
        assertThat(patient.getBindingCode()).isNotBlank();
        accounts.updateAccountId(patient.getId(), token(patient), "MiXeD123");
        LoginRequest login = new LoginRequest(); login.setIdentifier("MIXED123"); login.setPassword("Fixture-password");
        var response = auth.login(login);
        assertThat(response.userId()).isEqualTo(patient.getId());
        assertThat(response.role()).isEqualTo("PATIENT");
        assertThat(response.customExerciseToken()).isEqualTo(token(patient));
        assertThat(jdbc.queryForObject("SELECT account_id_normalized FROM users WHERE id=?", String.class, patient.getId())).isEqualTo("mixed123");
        login.setPassword("wrong-password");
        assertThatThrownBy(() -> auth.login(login)).isInstanceOf(AuthApiException.class);
    }

    @Test void legacyPasswordUpgradeAndNullPasswordAreSafe() {
        User patient = user("PATIENT"); patient.setPassword("legacy-fixture"); users.saveAndFlush(patient);
        LoginRequest login = new LoginRequest(); login.setIdentifier(patient.getEmail()); login.setPassword("legacy-fixture");
        auth.login(login); assertThat(users.findById(patient.getId()).orElseThrow().getPassword()).startsWith("$2");
        patient.setPassword(null); users.saveAndFlush(patient);
        assertThatThrownBy(() -> auth.login(login)).isInstanceOf(AuthApiException.class);
    }

    @Test void verifiedGoogleCreatesSamePatientAndNeverConvertsTherapist() {
        String subject = unique(), email = unique() + "@example.invalid";
        when(google.verify("synthetic-token")).thenReturn(new VerifiedGoogleIdentity(subject, email, "測試 Google"));
        var first = auth.googleLogin("synthetic-token");
        assertThat(first.role()).isEqualTo("PATIENT");
        assertThat(auth.googleLogin("synthetic-token").userId()).isEqualTo(first.userId());
        assertThat(users.findById(first.userId()).orElseThrow().getPassword()).isNull();
        User therapist = user("THERAPIST");
        when(google.verify("therapist-token")).thenReturn(new VerifiedGoogleIdentity(unique(), therapist.getEmail(), "測試"));
        assertThatThrownBy(() -> auth.googleLogin("therapist-token")).isInstanceOf(AuthApiException.class);
        assertThat(therapist.getRole()).isEqualTo("THERAPIST");
    }

    @Test void passwordResetUsesGeneratedActiveKeyAndBcrypt() {
        User patient = user("PATIENT");
        resets.requestReset(patient.getEmail());
        assertThat(resetCredentials.findByUserIdAndConsumedAtIsNull(patient.getId())).hasSize(1);
        // Known synthetic credential lets us exercise reset without disclosing or calling a provider.
        PasswordResetCredential credential = resetCredentials.findByUserIdAndConsumedAtIsNull(patient.getId()).get(0);
        credential.setCodeHash(resetHasher.hash(credential.getId(), "654321")); resetCredentials.saveAndFlush(credential);
        assertThat(jdbc.queryForObject("SELECT active_user_id FROM password_reset_requests WHERE id=?", Long.class, credential.getId())).isEqualTo(patient.getId());
        resets.resetPassword(patient.getEmail(), "654321", "New-fixture-password");
        assertThat(resetCredentials.findByUserIdAndConsumedAtIsNull(patient.getId())).isEmpty();
        assertThat(passwords.verify("New-fixture-password", patient.getPassword())).isNotEqualTo(PasswordService.PasswordMatch.NO_MATCH);
    }

    @Test void customPoseRulesRoundTripCreateEditAssignPatientAndLegacy() throws Exception {
        User therapist = user("THERAPIST"), patient = user("PATIENT"); bind(patient, therapist);
        var request = customRequest();
        var rules = mapper.readTree("[{\"measurement\":\"RIGHT_ELBOW_ANGLE\",\"targetAngleDegrees\":90,\"toleranceDegrees\":10,\"feedbackTooLow\":\"請伸直🙂\"}]");
        request.setPoseMeasurementRules(rules);
        custom.save(request.getId(), therapist.getId(), token(therapist), request);
        entities.flush(); entities.clear();
        assertThat(custom.get(request.getId(), therapist.getId(), token(therapist)).getPoseMeasurementRules()).isEqualTo(rules);
        request.setName("修改後🙂"); request.setPoseMeasurementRules(null);
        custom.save(request.getId(), therapist.getId(), token(therapist), request);
        customAssignments.assign(request.getId(), patient.getId(), therapist.getId(), token(therapist));
        assertThat(customAssignments.getPatientExercise(request.getId(), patient.getId(), token(patient)).getPoseMeasurementRules()).isEqualTo(rules);
        var legacy = customRequest(); custom.save(legacy.getId(), therapist.getId(), token(therapist), legacy);
        assertThat(custom.get(legacy.getId(), therapist.getId(), token(therapist)).getPoseMeasurementRules()).isEmpty();
        request.setPoseMeasurementRules(mapper.createArrayNode()); custom.save(request.getId(), therapist.getId(), token(therapist), request);
        assertThat(custom.get(request.getId(), therapist.getId(), token(therapist)).getPoseMeasurementRules()).isEmpty();
    }

    @Test void defaultAssignmentResultsAndServerUtcTimestamp() {
        User therapist = user("THERAPIST"), patient = user("PATIENT"); bind(patient, therapist);
        Exercise exercise = new Exercise(); exercise.setExerciseName("站姿抬腳式訓練"); exercise.setDescription("測試"); entities.persist(exercise); entities.flush();
        assignments.assign("DEFAULT", exercise.getId().toString(), patient.getId(), therapist.getId(), token(therapist));
        assertThat(assignments.getPatientAssignedExercises(patient.getId(), token(patient))).hasSize(1);
        var request = new TrainingSessionResultRequest(); request.setSessionId(UUID.randomUUID().toString()); request.setExerciseType("DEFAULT"); request.setExerciseId(exercise.getId().toString());
        request.setCompletedSets(2); request.setTargetSets(2); request.setCompletedReps(4); request.setTargetReps(2); request.setDurationSeconds(10L); request.setCompletionStatus("COMPLETED");
        var first = results.save(patient.getId(), token(patient), request);
        assertThat(results.save(patient.getId(), token(patient), request)).isEqualTo(first);
        assertThat(results.getMine(patient.getId(), token(patient))).hasSize(1);
        jdbc.update("INSERT INTO exercise_result(user_id,exercise_id,rep_count,is_complete) VALUES (?,?,1,1)", patient.getId(), exercise.getId());
        // DATETIME is a UTC wall clock, not java.sql.Timestamp in JVM default Asia/Taipei.
        var created = jdbc.queryForObject("SELECT created_at FROM exercise_result WHERE user_id=?", (rs,n)->rs.getObject(1,LocalDateTime.class), patient.getId());
        assertThat(java.time.Duration.between(created, LocalDateTime.now(ZoneOffset.UTC)).abs().toSeconds()).isLessThan(5);
        Long resultId=jdbc.queryForObject("SELECT id FROM exercise_result WHERE user_id=?",Long.class,patient.getId());
        assertThat(java.time.Duration.between(entities.find(ExerciseResult.class,resultId).getCreatedAt(),LocalDateTime.now(ZoneOffset.UTC)).abs().toSeconds()).isLessThan(5);
    }

    @Test void therapistRegistrationBindingAndPlanReadUpdateUseMySql() {
        var registered=therapistRegistration.register(new TherapistRegisterRequest("虛構治療師",unique()+"@example.invalid","Fixture-password"),unique());
        User therapist=users.findById(registered.userId()).orElseThrow(), patient=user("PATIENT");
        therapistBindings.bindPatient(patient.getBindingCode(),therapist.getId(),token(therapist));
        assertThat(therapistBindings.getPatients(therapist.getId(),token(therapist))).hasSize(1);
        String date=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).toString(), plan=unique();
        var item=new RehabPlanItemDto("wipeBody",0,2,3,false);
        var request=new RehabPlanRequest(patient.getId().toString(),plan,"therapist",date,"stroke",List.of(item));
        plans.savePlan(request,therapist.getId(),token(therapist)); entities.flush(); entities.clear();
        assertThat(plans.getPlan(patient.getId().toString(),java.time.LocalDate.parse(date),patient.getId(),token(patient)).items()).hasSize(1);
        plans.updatePlanItem(patient.getId().toString(),plan,"wipeBody",new RehabPlanItemDto("wipeBody",0,2,3,true),patient.getId(),token(patient));
        assertThat(plans.getPlan(patient.getId().toString(),java.time.LocalDate.parse(date),patient.getId(),token(patient)).items().get(0).done()).isTrue();
    }

    @Test void boundAndPeerChatReadReceiptAndAvatarBinaryRoundTrip() {
        User therapist = user("THERAPIST"), patient = user("PATIENT"), peer = user("PATIENT"); bind(patient, therapist);
        var contact = chats.getOrCreateConversation(patient.getId(), token(patient), therapist.getId(), "THERAPIST");
        chats.sendMessage(patient.getId(), token(patient), contact.id(), "你好🙂");
        chats.markAsRead(therapist.getId(), token(therapist), contact.id()); entities.flush(); entities.clear();
        assertThat(chats.getMessages(patient.getId(), token(patient), contact.id()).get(0).readAt()).isNotNull();
        var invite = new FriendRequestCreateDto(); invite.setFriendCode(peer.getFriendCode());
        var sent = friends.sendRequest(patient.getId(), token(patient), invite);
        var accept = new FriendRequestRespondDto(); accept.setAction("accept");
        friends.respondToRequest(peer.getId(), token(peer), ((Number)sent.get("requestId")).longValue(), accept);
        assertThat(chats.getOrCreateConversation(patient.getId(), token(patient), peer.getId(), "PEER")).isNotNull();
        byte[] jpeg = {(byte)0xff,(byte)0xd8,(byte)0xff,0,1,2,3};
        avatars.uploadCurrentUserAvatar(patient.getId(), token(patient), new MockMultipartFile("file","avatar.jpg","image/jpeg",jpeg));
        entities.flush(); entities.clear();
        assertThat(avatars.getUserAvatar(therapist.getId(), token(therapist), patient.getId()).bytes()).isEqualTo(jpeg);
    }

    @Test void jdbcVideoUploadReplaceRangeHeadersAndCascade() throws Exception {
        User patient = user("PATIENT"); TrainingHistoryEntity history = history(patient);
        byte[] original = new byte[1024 * 1024 + 73]; for (int i=0;i<original.length;i++) original[i]=(byte)(i%251);
        videos.upload(history.getId(), patient.getId(), new MockMultipartFile("file","fixture.mp4","video/mp4",original));
        assertThat(videos.readRange(history.getId(), 1024 * 1024, 73)).isEqualTo(java.util.Arrays.copyOfRange(original, 1024*1024, original.length));
        mvc.perform(get("/api/training-history/{id}/video",history.getId()).header("X-User-Id",patient.getId()).header("X-Custom-Exercise-Token",token(patient)).header("Range","bytes=10-20"))
            .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 10-20/"+original.length)).andExpect(header().string("Content-Length","11"))
            .andExpect(content().bytes(java.util.Arrays.copyOfRange(original,10,21)));
        mvc.perform(get("/api/training-history/{id}/video",history.getId()).header("X-User-Id",patient.getId()).header("X-Custom-Exercise-Token",token(patient)).header("Range","bytes=999999999-"))
            .andExpect(status().isRequestedRangeNotSatisfiable());
        byte[] replacement = {1,2,3,4,5}; videos.upload(history.getId(), patient.getId(), new MockMultipartFile("file","new.mp4","video/mp4",replacement));
        assertThat(videos.readRange(history.getId(),0,5)).isEqualTo(replacement);
        mvc.perform(get("/api/training-history/{id}/video",history.getId()).header("X-User-Id",patient.getId()).header("X-Custom-Exercise-Token",token(patient)))
            .andExpect(status().isOk()).andExpect(content().bytes(replacement));
        histories.delete(history); histories.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_history_video WHERE history_id=?",Integer.class,history.getId())).isZero();
    }

    @Test void videoMissingOwnershipAndBadTokenAreRejected() throws Exception {
        User patient = user("PATIENT"), other = user("PATIENT"); var history = history(patient);
        assertThatThrownBy(() -> videos.upload(history.getId(),other.getId(),new MockMultipartFile("file","x.mp4","video/mp4",new byte[]{1}))).isInstanceOf(TrainingHistoryApiException.class);
        mvc.perform(get("/api/training-history/{id}/video",history.getId()).header("X-User-Id",patient.getId()).header("X-Custom-Exercise-Token",token(patient))).andExpect(status().isNotFound());
        mvc.perform(get("/api/training-history/{id}/video",history.getId()).header("X-User-Id",patient.getId()).header("X-Custom-Exercise-Token","invalid")).andExpect(status().isForbidden());
    }

    @Test void accountDeleteCleansRestrictiveGraphAndPreservesOtherUsers() {
        User patient = user("PATIENT"), therapist = user("THERAPIST"), unrelated = user("PATIENT"); bind(patient,therapist);
        var chat = chats.getOrCreateConversation(patient.getId(),token(patient),therapist.getId(),"THERAPIST"); chats.sendMessage(therapist.getId(),token(therapist),chat.id(),"測試");
        var history = history(patient); var otherHistory = history(unrelated);
        videos.upload(history.getId(),patient.getId(),new MockMultipartFile("file","x.mp4","video/mp4",new byte[]{1,2,3}));
        String plan = unique(); jdbc.update("INSERT INTO rehab_plans(plan_id,patient_id,created_by,plan_date,condition_type) VALUES (?,?,?,CURRENT_DATE,'stroke')",plan,patient.getId(),"therapist");
        Long planId = jdbc.queryForObject("SELECT id FROM rehab_plans WHERE plan_id=?",Long.class,plan);
        jdbc.update("INSERT INTO rehab_plan_items(rehab_plan_id,exercise_id,item_order,sets,reps_per_set) VALUES (?, 'wipeBody',0,1,2)",planId);
        jdbc.update("INSERT INTO training_session_results(session_id,patient_id,exercise_type,exercise_id,exercise_name,completed_sets,completed_reps,target_sets,target_reps,started_at,completed_at,duration_seconds,completion_status,score,created_at) VALUES (?,?,'DEFAULT','fixture','測試',1,1,1,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),1,'COMPLETED',100,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),patient.getId());
        accounts.deleteAccount(patient.getId(),token(patient),"Fixture-password",null); entities.clear();
        assertThat(users.findById(patient.getId())).isEmpty();
        assertThat(users.findById(therapist.getId())).isPresent();
        assertThat(histories.findById(otherHistory.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM training_history_video WHERE history_id=?",Integer.class,history.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rehab_plan_items WHERE rehab_plan_id=?",Integer.class,planId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_conversations WHERE id=?",Integer.class,chat.id())).isZero();
    }

    @Test void productionResearchRemainsDisabledAndUngrantableByPatient() throws Exception {
        User patient = user("PATIENT"), therapist = user("THERAPIST");
        assertThatThrownBy(() -> productionResearch.setConsent(patient.getId(),token(patient),true,"fixture-v1")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(authority.canManage(therapist)).isFalse(); assertThat(authority.canReview(patient)).isFalse();
        assertThatThrownBy(() -> authority.requireManager(therapist)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        mvc.perform(get("/api/ml-research/consent").header("X-User-Id", patient.getId())
                .header("X-Custom-Exercise-Token", token(patient)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
            .andExpect(jsonPath("$.unavailableReason").value("RESEARCH_COLLECTION_NOT_ENABLED"));
        mvc.perform(get("/api/ml-research/consent").header("X-User-Id", patient.getId())
                .header("X-Custom-Exercise-Token", "invalid-fixture-token"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ml-research/consent").header("X-User-Id", therapist.getId())
                .header("X-Custom-Exercise-Token", token(therapist)))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/ml-research/consent").header("X-User-Id", patient.getId())
                .header("X-Custom-Exercise-Token", token(patient))
                .contentType("application/json").content("{\"agree\":true,\"version\":\"fixture-v1\"}"))
            .andExpect(status().isServiceUnavailable());
        assertThat(consents.findById(patient.getId())).isEmpty();
    }

    @Test void noRetentionPolicyAndUnrelatedTherapistCannotUploadOrRead() throws Exception {
        User patient=user("PATIENT"), unrelated=user("THERAPIST"); grant(unrelated,true,true,false);
        assertThatThrownBy(() -> testResearch().setConsent(patient.getId(),token(patient),true,"fixture-v1"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(authority.canAnnotate(unrelated)).isTrue();
        assertThat(productionResearch.list(unrelated.getId(),token(unrelated),0,20).getTotalElements()).isZero();
    }

    @Test void managementGrantReviewRequestDecisionAndAuditUseMySql() {
        User manager=user("THERAPIST"), reviewer=user("THERAPIST"), patient=user("PATIENT");
        bind(patient,reviewer); grant(manager,false,false,true);
        authority.setGrant(manager.getId(),token(manager),reviewer.getId(),true,false,false);
        var request=authority.requestReview(reviewer.getId(),token(reviewer));
        assertThat(authority.pendingRequests(manager.getId(),token(manager))).hasSize(1);
        authority.decide(manager.getId(),token(manager),request.id(),true); entities.flush(); entities.clear();
        assertThat(authority.me(reviewer.getId(),token(reviewer)).canReview()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM research_grant_audit WHERE target_user_id=?",Integer.class,reviewer.getId())).isEqualTo(2);
    }

    @Test void deletingTherapistRemovesOwnedCustomAssignmentsNotPatientHistory() throws Exception {
        User therapist=user("THERAPIST"), patient=user("PATIENT"); bind(patient,therapist); var history=history(patient);
        var exercise=customRequest(); custom.save(exercise.getId(),therapist.getId(),token(therapist),exercise);
        customAssignments.assign(exercise.getId(),patient.getId(),therapist.getId(),token(therapist)); entities.flush();
        accounts.deleteAccount(therapist.getId(),token(therapist),"Fixture-password",null); entities.clear();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM custom_rehab_exercises WHERE id=?",Integer.class,exercise.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM custom_exercise_assignments WHERE custom_exercise_id=?",Integer.class,exercise.getId())).isZero();
        assertThat(histories.findById(history.getId())).isPresent(); assertThat(users.findById(patient.getId())).isPresent();
    }

    @Test void videoPacketLimitRejectsUnsafeHundredMiBConfiguration() {
        User patient=user("PATIENT"); var history=history(patient);
        var oversized=new MockMultipartFile("file","large.mp4","video/mp4",new byte[]{1}) {
            @Override public long getSize(){return 65L*1024*1024;}
        };
        var overridden=new TrainingHistoryVideoService(histories,users,bindings,identity,jdbc,100L*1024*1024);
        assertThatThrownBy(()->overridden.upload(history.getId(),patient.getId(),oversized))
            .isInstanceOf(TrainingHistoryApiException.class)
            .extracting(e->((TrainingHistoryApiException)e).getStatus()).isEqualTo(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test void deletingResearchParticipantRemovesPayloadButKeepsMinimalAudit() throws Exception {
        User patient=user("PATIENT"), manager=user("THERAPIST"); grant(manager,false,false,true);
        retention.createPolicy(manager.getId(),token(manager),"fixture-delete-v1",1,Instant.now().minusSeconds(1),"SYNTHETIC-ONLY");
        var research=testResearch(); research.setConsent(patient.getId(),token(patient),true,"fixture-v1");
        var sample=research.upload(patient.getId(),token(patient),samplePayload());
        accounts.deleteAccount(patient.getId(),token(patient),"Fixture-password",null); entities.clear();
        assertThat(samples.findById(sample.id())).isEmpty(); assertThat(consents.findById(patient.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM research_retention_events WHERE sample_id=? AND reason='ACCOUNT_DELETED'",Integer.class,sample.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM research_audit WHERE actor_user_id=?",Integer.class,patient.getId())).isPositive();
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedAccountDeletionRollsBackEarlierRestrictiveCleanup() {
        // Explicit bounded committed fixture is necessary to observe rollback from outside the transaction.
        var tx=new TransactionTemplate(transactionManager);
        User[] fixture=tx.execute(status->{
            User manager=user("THERAPIST"), patient=user("PATIENT"); grant(manager,false,false,true); bind(patient,manager);
            return new User[]{manager,patient};
        });
        try {
            assertThatThrownBy(()->accounts.deleteAccount(fixture[0].getId(),token(fixture[0]),"Fixture-password",null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(users.findById(fixture[0].getId())).isPresent();
            assertThat(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(fixture[1].getId(),fixture[0].getId(),"THERAPIST")).isTrue();
        } finally {
            tx.executeWithoutResult(status->{
                grants.deleteAll(grants.findAll().stream().filter(g->g.getUserId().equals(fixture[0].getId())).toList()); grants.flush();
                accounts.deleteAccount(fixture[1].getId(),token(fixture[1]),"Fixture-password",null);
                accounts.deleteAccount(fixture[0].getId(),token(fixture[0]),"Fixture-password",null);
            });
        }
    }

    @Test void researchConsentUploadReviewExportWithdrawalAndExpiryUseRealMySql() throws Exception {
        User patient=user("PATIENT"), annotator=user("THERAPIST"), reviewer=user("THERAPIST"), manager=user("THERAPIST");
        bind(patient,annotator); bind(patient,reviewer); grant(annotator,true,false,false); grant(reviewer,true,true,false); grant(manager,false,false,true);
        retention.createPolicy(manager.getId(),token(manager),"fixture-only-v1",1,Instant.now().minusSeconds(1),"SYNTHETIC-NOT-ETHICS-APPROVAL");
        ResearchDataService research = testResearch(); research.setConsent(patient.getId(),token(patient),true,"fixture-v1");
        ObjectNode payload = samplePayload(); var sample=research.upload(patient.getId(),token(patient),payload);
        entities.flush(); entities.clear();
        ResearchSampleEntity stored=samples.findById(sample.id()).orElseThrow();
        assertThat(java.time.Duration.between(Instant.parse(payload.path("capturedAt").asText()),stored.getCapturedAt()).abs().toMillis()).isLessThan(1);
        assertThat(jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,captured_at,UTC_TIMESTAMP(6))) FROM research_samples WHERE id=?",Long.class,sample.id())).isLessThan(10);
        assertThat(research.upload(patient.getId(),token(patient),payload).id()).isEqualTo(sample.id());
        research.label(annotator.getId(),token(annotator),sample.id(),"meets_requirement","虛構測試","fixture-label-v1","standing-knee-raise-v1");
        research.submitLabel(annotator.getId(),token(annotator),sample.id());
        assertThatThrownBy(() -> research.reviewLabel(annotator.getId(),token(annotator),sample.id(),true,"自審")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        // Give the annotator review permission too: independent-review prohibition still holds.
        grant(annotator,true,true,false);
        assertThatThrownBy(() -> research.reviewLabel(annotator.getId(),token(annotator),sample.id(),true,"自審")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        research.reviewLabel(reviewer.getId(),token(reviewer),sample.id(),true,"虛構資料通過");
        assertThatThrownBy(() -> research.label(annotator.getId(),token(annotator),sample.id(),"insufficient_range","覆寫","v2","standing-knee-raise-v1")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        ResearchManagementService exporter = new ResearchManagementService(authority,annotations,samples,consents,exportAudits,validator,new ResearchTrainingFeatureValidator(),mapper,"fixture-v1");
        assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager)),"manifest.json")).contains("\"sampleCount\":1");
        research.setConsent(patient.getId(),token(patient),false,"fixture-v1");
        assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager)),"manifest.json")).contains("\"sampleCount\":0");
        ResearchSampleEntity expired=samples.findById(sample.id()).orElseThrow(); expired.setExpiresAt(Instant.now().minusSeconds(1)); samples.saveAndFlush(expired);
        assertThat(retention.processExpiredAsManager(manager.getId(),token(manager))).isEqualTo(1);
        entities.flush();
        assertThat(retention.processExpiredAsManager(manager.getId(),token(manager))).isZero();
        assertThat(samples.findById(sample.id())).isEmpty();
    }

    @Test void fourHandActionsConsentReviewExportAndExpiryRoundTripInMySql() throws Exception {
        User patient=user("PATIENT"), annotator=user("THERAPIST"), reviewer=user("THERAPIST"), manager=user("THERAPIST");
        bind(patient,annotator); bind(patient,reviewer);
        grant(annotator,true,true,false); grant(reviewer,true,true,false); grant(manager,false,false,true);
        retention.createPolicy(manager.getId(),token(manager),"g5-fixture-only",1,Instant.now().minusSeconds(1),"SYNTHETIC-NOT-ETHICS-APPROVAL");
        var research=testResearch();
        org.springframework.test.util.ReflectionTestUtils.setField(research,"handConsentVersion","fixture-v1");
        research.setConsent(patient.getId(),token(patient),true,"fixture-v1");
        var exporter=new ResearchManagementService(authority,annotations,samples,consents,exportAudits,validator,new ResearchTrainingFeatureValidator(),mapper,"fixture-v1");
        org.springframework.test.util.ReflectionTestUtils.setField(exporter,"handConsentVersion","fixture-v1");
        for(var definition:ResearchActionRegistry.HANDS) {
            var payload=ResearchHandContractTest.fixture(definition.actionId());
            payload.put("sampleId",unique());
            var sample=research.upload(patient.getId(),token(patient),payload);
            entities.flush(); entities.clear();
            assertThat(samples.findById(sample.id()).orElseThrow().getMovementSide()).isEqualTo("unknown");
            assertThat(research.upload(patient.getId(),token(patient),payload).id()).isEqualTo(sample.id());
            assertThatThrownBy(()->research.label(annotator.getId(),token(annotator),sample.id(),"meets_requirement","fixture","wrong",definition.version()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            research.label(annotator.getId(),token(annotator),sample.id(),"meets_requirement","synthetic only","hand-research-v1",definition.version());
            research.submitLabel(annotator.getId(),token(annotator),sample.id());
            assertThatThrownBy(()->research.reviewLabel(annotator.getId(),token(annotator),sample.id(),true,"self"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager),definition.actionId()),"manifest.json")).contains("\"sampleCount\":0");
            research.reviewLabel(reviewer.getId(),token(reviewer),sample.id(),true,"synthetic review");
            entities.flush();
            assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager),definition.actionId()),"manifest.json"))
                .contains("\"sampleCount\":1","\"schemaVersion\":2",definition.actionId());
            assertThatThrownBy(()->research.label(annotator.getId(),token(annotator),sample.id(),"unstable_motion","overwrite","hand-research-v1",definition.version()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        research.setConsent(patient.getId(),token(patient),false,"fixture-v1");
        for(var definition:ResearchActionRegistry.HANDS) {
            assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager),definition.actionId()),"manifest.json")).contains("\"sampleCount\":0");
        }
        assertThat(productionResearch.consent(patient.getId(),token(patient)).handAvailable()).isFalse();
    }

    private ResearchDataService testResearch() {
        // Only this test-local instance accepts synthetic consent; production bean stays disabled.
        return new ResearchDataService(users,bindings,identity,consents,samples,annotations,revisions,audits,validator,authority,retention,mapper,true,"fixture-v1");
    }

    @Test void syntheticActionRoundTripUsesMySqlWithoutProductionRegistration() throws Exception {
        User patient=user("PATIENT"), annotator=user("THERAPIST"), reviewer=user("THERAPIST"), manager=user("THERAPIST");
        bind(patient,annotator); bind(patient,reviewer);
        grant(annotator,true,true,false); grant(reviewer,true,true,false); grant(manager,false,false,true);
        retention.createPolicy(manager.getId(),token(manager),"synthetic-only-v1",1,Instant.now().minusSeconds(1),"SYNTHETIC-NOT-ETHICS-APPROVAL");
        var registry = SyntheticResearchContract.REGISTRY;
        var testValidator = new ResearchSampleValidator(mapper, registry);
        var research = new ResearchDataService(users,bindings,identity,consents,samples,annotations,revisions,
            audits,testValidator,authority,retention,mapper,true,"fixture-v1");
        research.setConsent(patient.getId(),token(patient),true,"fixture-v1");
        ObjectNode payload=samplePayload();
        payload.put("actionId","synthetic_test_only");
        payload.put("actionDefinitionVersion","synthetic-v1");
        payload.putArray("featureNames").add("duration_seconds");
        payload.putArray("features").add(0.3);
        assertThatThrownBy(() -> validator.validate(payload)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var uploaded=research.upload(patient.getId(),token(patient),payload);
        entities.flush(); entities.clear();
        assertThat(samples.findById(uploaded.id()).orElseThrow().getPayloadJson()).contains("synthetic-v1");
        assertThat(research.upload(patient.getId(),token(patient),payload).id()).isEqualTo(uploaded.id());
        assertThat(research.detail(annotator.getId(),token(annotator),uploaded.id()).sample().actionId()).isEqualTo("synthetic_test_only");
        research.label(annotator.getId(),token(annotator),uploaded.id(),"test_match","fixture","synthetic-label-v1","synthetic-v1");
        research.submitLabel(annotator.getId(),token(annotator),uploaded.id());
        assertThatThrownBy(() -> research.reviewLabel(annotator.getId(),token(annotator),uploaded.id(),true,"fixture"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var exporter = new ResearchManagementService(authority,annotations,samples,consents,exportAudits,
            testValidator,new ResearchTrainingFeatureValidator(registry),mapper,"fixture-v1");
        assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager),"synthetic_test_only"),"manifest.json"))
            .contains("\"sampleCount\":0");
        research.reviewLabel(reviewer.getId(),token(reviewer),uploaded.id(),true,"synthetic fixture only");
        assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager),"synthetic_test_only"),"manifest.json"))
            .contains("\"sampleCount\":1", "synthetic-v1");
        assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager)),"manifest.json"))
            .contains("\"sampleCount\":0");
        research.setConsent(patient.getId(),token(patient),false,"fixture-v1");
        assertThat(zipEntry(exporter.exportApproved(manager.getId(),token(manager),"synthetic_test_only"),"manifest.json"))
            .contains("\"sampleCount\":0");
        assertThat(productionResearch.consent(patient.getId(),token(patient)).available()).isFalse();
    }
    private void grant(User user,boolean annotate,boolean review,boolean manage) {
        var existing=entities.createQuery("select g from ResearchGrantEntity g where g.userId=:id and g.studyId=:study",ResearchGrantEntity.class).setParameter("id",user.getId()).setParameter("study",ResearchAuthorityService.STUDY_ID).getResultList();
        var g=existing.isEmpty()?new ResearchGrantEntity():existing.get(0); g.setUserId(user.getId()); g.setStudyId(ResearchAuthorityService.STUDY_ID); g.setCanAnnotate(annotate); g.setCanReview(review); g.setCanManage(manage); g.setUpdatedAt(Instant.now());
        if(existing.isEmpty()) entities.persist(g); entities.flush();
    }
    private ObjectNode samplePayload() {
        ObjectNode json=mapper.createObjectNode(); json.put("schemaVersion",1); json.put("actionId","standing_knee_raise"); json.put("sampleId",unique()); json.put("movementSide","left"); json.put("cameraView","front"); json.put("capturedAt",Instant.now().toString());
        json.putObject("segment").put("startMs",0).put("endMs",300);
        var names=json.putArray("featureNames"); for(String n:List.of("peak_leg_height","minimum_hip_angle_deg","minimum_knee_angle_deg","peak_abs_trunk_lean_deg","duration_seconds")) names.add(n);
        var values=json.putArray("features"); for(double v:new double[]{-1,180,180,0,0.3}) values.add(v);
        var frames=json.putArray("frames"); for(int t=0;t<=300;t+=100) {
            var f=frames.addObject(); f.put("timestampMs",t); var p=f.putArray("landmarks");
            for(int i=0;i<17;i++){ double x=i==6||i==12||i==14||i==16?2:0; double y=i==11||i==12?1:i==13||i==14?2:i==15||i==16?3:0; p.addArray().add(x).add(y); }
            var c=f.putArray("confidence"); for(int i=0;i<17;i++) c.add(0.9); f.putObject("angles").put("hipDeg",180).put("kneeDeg",180).put("trunkLeanDeg",0);
        } return json;
    }
    private String zipEntry(byte[] zip,String name) throws Exception {
        try(var input=new ZipInputStream(new ByteArrayInputStream(zip))) { for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()) if(name.equals(entry.getName())) return new String(input.readAllBytes(),StandardCharsets.UTF_8); }
        throw new AssertionError("Missing ZIP entry");
    }
    private User user(String role) {
        User user=new User(); user.setEmail(unique()+"@example.invalid"); user.setName("虛構測試🙂"); user.setRole(role); user.setPassword(passwords.encode("Fixture-password")); user.setFriendCode(unique().replace("-", "").substring(0,12).toUpperCase()); user.setBindingCode(unique().replace("-", "").substring(0,12).toUpperCase()); return users.saveAndFlush(user);
    }
    private String token(User user){return identity.issueToken(user);}
    private String unique(){return UUID.randomUUID().toString();}
    private void bind(User patient,User therapist){var b=new UserBinding(); b.setPatient(patient); b.setLinkedUser(therapist); b.setRelationship("THERAPIST"); bindings.saveAndFlush(b);}
    private TrainingHistoryEntity history(User patient){var h=new TrainingHistoryEntity(); h.setUser(patient); h.setActionName("虛構站姿抬腳🙂"); h.setDifficulty(1); h.setDurationSeconds(10); h.setCompletedReps(1); h.setTargetReps(2); h.setMistakeCount(0); h.setClientTimestamp(unique().substring(0,32)); h.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)); return histories.saveAndFlush(h);}
    private CustomRehabExerciseDto customRequest() throws Exception {
        var r=new CustomRehabExerciseDto(); r.setId(unique()); r.setName("虛構抬腳🙂"); r.setDescription("測試"); r.setCreatedAt(Instant.now().toString()); r.setUpdatedAt(Instant.now().toString()); r.setRepetitions(2); r.setSets(2); r.setHoldSeconds(1.5); r.setRestSeconds(1.0); r.setDuration(2.0); r.setKeyframes(mapper.readTree("[{\"id\":\"a\",\"time\":0,\"jointRotations\":{}},{\"id\":\"b\",\"time\":2,\"jointRotations\":{}}]")); r.setEvaluationRules(mapper.createArrayNode()); return r;
    }
}
