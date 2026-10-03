package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Explicit operator command, NOT a production runner/API and NOT run by normal mvn test.
 * Uses existing localhost schema/CRUD only; writes only named fake accounts plus the owner-authorized policy.
 */
@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS, properties = {
    "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
    "research.collection-enabled=true", "research.consent-version=hand-research-consent-v1",
    "research.hand-consent-version=hand-research-consent-v1"
})
@EnabledIfEnvironmentVariable(named = "RESEARCH_ACTIVATION_SETUP", matches = "true")
class ResearchActivationSetupTest {
    @Autowired UserRepository users;
    @Autowired PasswordService passwords;
    @Autowired UserBindingRepository bindings;
    @Autowired ResearchGrantRepository grants;
    @Autowired ResearchGrantAuditRepository grantAudits;
    @Autowired ResearchConsentRepository consents;
    @Autowired ResearchSampleRepository samples;
    @Autowired ResearchAnnotationRepository annotations;
    @Autowired ResearchAnnotationRevisionRepository revisions;
    @Autowired ResearchAuthorityService authority;
    @Autowired ResearchRetentionService retention;
    @Autowired ResearchDataService data;
    @Autowired CustomExerciseIdentityService identity;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test void seedOrResetOnlyNamedDemoRecordsUsingExistingServices() {
        assertThat(System.getenv("DB_URL")).startsWith("jdbc:mysql://127.0.0.1:3306/rehab_r2_validation?");
        assertThat(System.getenv("RENDER")).isNullOrEmpty();
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("rehab_r2_validation");
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.11");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User patient = user("demo_patient", "PATIENT", "DEMO 患者", "DEMO_PATIENT_PASSWORD");
            User therapist = user("demo_therapist", "THERAPIST", "DEMO 標註治療師", "DEMO_THERAPIST_PASSWORD");
            User reviewer = user("demo_reviewer", "THERAPIST", "DEMO 獨立審核治療師", "DEMO_REVIEWER_PASSWORD");
            User manager = user("demo_manager", "THERAPIST", "DEMO 研究管理者", "DEMO_MANAGER_PASSWORD");
            for (var linked : List.of(therapist, reviewer, manager)) bind(patient, linked);
            if (grants.findByUserIdAndStudyId(manager.getId(), ResearchAuthorityService.STUDY_ID).isEmpty()) {
                var grant = new ResearchGrantEntity();
                grant.setUserId(manager.getId()); grant.setStudyId(ResearchAuthorityService.STUDY_ID);
                grant.setCanManage(true); grant.setGrantedByUserId(manager.getId()); grant.setUpdatedAt(Instant.now());
                grants.saveAndFlush(grant);
                var audit = new ResearchGrantAuditEntity();
                audit.setActorUserId(manager.getId()); audit.setTargetUserId(manager.getId());
                audit.setStudyId(ResearchAuthorityService.STUDY_ID); audit.setAction("OWNER_DEMO_MANAGER_SETUP");
                audit.setCreatedAt(Instant.now()); grantAudits.save(audit);
            }
            if (!authority.canAnnotate(therapist)) authority.setGrant(manager.getId(), token(manager), therapist.getId(), true, false, false);
            if (!authority.canReview(reviewer)) authority.setGrant(manager.getId(), token(manager), reviewer.getId(), false, true, false);
            // Preserve any current effective policy. Do not overwrite/duplicate an existing policy.
            if (retention.currentPolicy().isEmpty()) {
                retention.createPolicy(manager.getId(), token(manager), "hand-retention-v1", 90,
                    Instant.now().minusSeconds(1), "OWNER-AUTH-20261003-G5");
            }
            var available = data.consent(patient.getId(), token(patient));
            assertThat(available.available()).isTrue(); assertThat(available.handAvailable()).isTrue();
            if (!available.active() || !"hand-research-consent-v1".equals(available.consentVersion())) {
                data.setConsent(patient.getId(), token(patient), true, "hand-research-consent-v1");
            }
            var consent = consents.findById(patient.getId()).orElseThrow();
            if (!"DEV-SUBJECT-001".equals(consent.getSubjectId())) {
                assertThat(samples.findByParticipantUserId(patient.getId())).isEmpty();
                consent.setSubjectId("DEV-SUBJECT-001"); consents.saveAndFlush(consent);
            }
            var existing = samples.findByParticipantUserIdAndClientSampleId(patient.getId(), "DEMO-HAND-001");
            var payload = existing.map(s -> {
                var node = (com.fasterxml.jackson.databind.node.ObjectNode) data.detail(patient.getId(), token(patient), s.getId()).payload().deepCopy();
                node.put("sampleId", "DEMO-HAND-001"); return node;
            }).orElseGet(() -> DemoHandSampleFactory.create(mapper, "DEMO-HAND-001", Instant.now()));
            var result = data.upload(patient.getId(), token(patient), payload);
            assertThat(data.upload(patient.getId(), token(patient), payload).id()).isEqualTo(result.id());
            assertThat(data.detail(therapist.getId(), token(therapist), result.id()).payload().path("frames").get(0).path("landmarks")).hasSize(21);
            if ("reset".equals(System.getenv("RESEARCH_SETUP_ACTION"))) {
                // Explicit operator reset of this synthetic sample only; never alter any other annotation.
                revisions.deleteAll(revisions.findBySampleId(result.id()));
                annotations.findById(result.id()).ifPresent(annotations::delete);
                annotations.flush();
            }
            assertThat(samples.findByParticipantUserId(patient.getId()).stream()
                .filter(s -> "DEMO-HAND-001".equals(s.getClientSampleId())).count()).isEqualTo(1);
        });
    }

    private User user(String account, String role, String name, String environment) {
        var existing = users.findByAccountId(account);
        if (existing.isPresent()) {
            User user = existing.get();
            assertThat(user.getEmail()).isEqualTo(account + "@demo.invalid");
            assertThat(user.getName()).isEqualTo(name); assertThat(user.getRole()).isEqualTo(role);
            assertThat(passwords.verify(System.getenv(environment), user.getPassword()))
                .isNotEqualTo(PasswordService.PasswordMatch.NO_MATCH);
            return user; // Never overwrite an existing account/password.
        }
        String password = System.getenv(environment);
        assertThat(password).isNotBlank(); assertThat(passwords.isAcceptableNewPassword(password)).isTrue();
        assertThat(users.findByEmail(account + "@demo.invalid")).isEmpty();
        var user = new User(); user.setAccountId(account); user.setEmail(account + "@demo.invalid");
        user.setName(name); user.setRole(role); user.setPassword(passwords.encode(password));
        return users.saveAndFlush(user);
    }
    private void bind(User patient, User linked) {
        if (!bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(patient.getId(), linked.getId(), "THERAPIST")) {
            var binding = new UserBinding(); binding.setPatient(patient); binding.setLinkedUser(linked);
            binding.setRelationship("THERAPIST"); bindings.saveAndFlush(binding);
        }
    }
    private String token(User user) { return identity.issueToken(user); }
}
