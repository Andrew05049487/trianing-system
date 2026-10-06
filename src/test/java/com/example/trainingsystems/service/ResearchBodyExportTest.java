package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.*;

class ResearchBodyExportTest {
    final ObjectMapper mapper=new ObjectMapper();
    ResearchAuthorityService authority; ResearchAnnotationRepository annotations;
    ResearchSampleRepository samples; ResearchConsentRepository consents;
    ResearchManagementService service; ResearchSampleEntity sample;
    ResearchAnnotationEntity annotation; ResearchConsentEntity consent;
    @BeforeEach void setup() {
        authority=mock(ResearchAuthorityService.class);annotations=mock(ResearchAnnotationRepository.class);
        samples=mock(ResearchSampleRepository.class);consents=mock(ResearchConsentRepository.class);
        service=new ResearchManagementService(authority,annotations,samples,consents,mock(ResearchExportAuditRepository.class),
            new ResearchSampleValidator(mapper),new ResearchTrainingFeatureValidator(),mapper,"fake-consent");
        when(authority.authenticated(9L,"token")).thenReturn(new User());
        annotation=new ResearchAnnotationEntity();annotation.setSampleId("sample-1");annotation.setTherapistUserId(87654L);
        annotation.setReviewerUserId(3L);annotation.setReviewedAt(Instant.now());annotation.setStatus("APPROVED");
        annotation.setLabel("insufficient_range");annotation.setLabelVersion("body-attempt-label-v1");
        annotation.setActionDefinitionVersion(ResearchBodyAttemptValidator.DEFINITION);
        when(annotations.findByStatus(eq("APPROVED"),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(annotation)));
        sample=new ResearchSampleEntity();sample.setId("sample-1");sample.setClientSampleId("ordinary-id");sample.setSubjectId("pseudonym-1");
        sample.setParticipantUserId(12345L);sample.setExpiresAt(Instant.now().plusSeconds(3600));
        sample.setPayloadJson(ResearchBodyAttemptTest.fixture().toString());
        when(samples.findById("sample-1")).thenReturn(Optional.of(sample));
        consent=new ResearchConsentEntity();consent.setUserId(12345L);consent.setSubjectId("pseudonym-1");
        consent.setActive(true);consent.setConsentVersion("fake-consent");
        when(consents.findById(12345L)).thenReturn(Optional.of(consent));
    }
    @Test void sourceIsExplicitAndManifestRetainsAnonymousGroups() throws Exception {
        assertThatThrownBy(()->service.exportApproved(9L,"token","standing_knee_raise",3,null)).hasMessageContaining("400");
        var zip=export("tv_pi");String manifest=entry(zip,"manifest.json"),body=entry(zip,"samples/sample-1.json");
        assertThat(manifest).contains("\"schemaVersion\":3","tv_pi","sessionGrouping","attemptGrouping","modelInputVersion","poseModelVersion","extractorVersion");
        assertThat(body).contains("session_1","attempt_1","pseudonym-1").doesNotContain("patientId","therapistId","12345","87654");
        assertThat(entry(export("phone"),"manifest.json")).contains("\"sampleCount\":0");
        assertThat(entry(zip,"labels.csv")).contains("insufficient_range").doesNotContain("87654");
    }
    @ParameterizedTest @ValueSource(strings={"REJECTED","NEEDS_RESAMPLE","EXCLUDED"})
    void nonActiveIsExcluded(String disposition) throws Exception {
        sample.setDisposition(disposition);emptyExport();
    }
    @Test void revokedExpiredDeletedDemoAndUnassessableAreExcluded() throws Exception {
        consent.setActive(false);emptyExport();consent.setActive(true);
        sample.setExpiresAt(Instant.now().minusSeconds(1));emptyExport();sample.setExpiresAt(Instant.now().plusSeconds(3600));
        sample.setClientSampleId("DEMO-001");emptyExport();sample.setClientSampleId("ordinary-id");
        annotation.setLabel("unassessable");emptyExport();annotation.setLabel("meets_requirement");
        when(samples.findById("sample-1")).thenReturn(Optional.empty());emptyExport();
    }
    @Test void invalidFeaturesOrVersionNeverExport() throws Exception {
        var payload=ResearchBodyAttemptTest.fixture();payload.put("extractorVersion","unknown");
        sample.setPayloadJson(payload.toString());emptyExport();
        sample.setPayloadJson(ResearchBodyAttemptTest.fixture().toString());annotation.setLabelVersion("research-v1");emptyExport();
    }
    @Test void independentReviewerAndCurrentConsentVersionRequired() throws Exception {
        annotation.setReviewerUserId(annotation.getTherapistUserId());emptyExport();annotation.setReviewerUserId(3L);
        consent.setConsentVersion("old-consent");emptyExport();
    }
    byte[] export(String source){return service.exportApproved(9L,"token","standing_knee_raise",3,source);}
    void emptyExport() throws Exception {assertThat(entry(export("tv_pi"),"manifest.json")).contains("\"sampleCount\":0");}
    String entry(byte[] bytes,String name) throws Exception {
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()) if(e.getName().equals(name))
                return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }
        throw new AssertionError(name);
    }
}
