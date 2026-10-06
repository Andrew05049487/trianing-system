package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import com.example.trainingsystems.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ResearchBodyReviewTest {
    final ObjectMapper mapper=new ObjectMapper();
    UserRepository users; UserBindingRepository bindings; CustomExerciseIdentityService identity;
    ResearchConsentRepository consents; ResearchSampleRepository samples;
    ResearchAnnotationRepository annotations; ResearchAnnotationRevisionRepository revisions;
    ResearchAuthorityService authority; ResearchRetentionService retention;
    ResearchDataService service; ResearchSampleEntity sample; ResearchAnnotationEntity annotation;
    @BeforeEach void setup() {
        users=mock(UserRepository.class);bindings=mock(UserBindingRepository.class);identity=mock(CustomExerciseIdentityService.class);
        consents=mock(ResearchConsentRepository.class);samples=mock(ResearchSampleRepository.class);
        annotations=mock(ResearchAnnotationRepository.class);revisions=mock(ResearchAnnotationRevisionRepository.class);
        authority=mock(ResearchAuthorityService.class);retention=mock(ResearchRetentionService.class);
        service=new ResearchDataService(users,bindings,identity,consents,samples,annotations,revisions,
            mock(ResearchAuditRepository.class),new ResearchSampleValidator(mapper),authority,retention,mapper,true,"test-v1");
        ReflectionTestUtils.setField(service,"bodyAssignments",mock(ResearchBodyAssignmentService.class));
        for(long id:new long[]{1,2,3,4}) {
            var user=new User();user.setId(id);user.setRole(id==1?"PATIENT":"THERAPIST");
            when(users.findById(id)).thenReturn(Optional.of(user));when(identity.isValid(user,"token")).thenReturn(true);
            when(authority.canAnnotate(user)).thenReturn(id!=4);
            when(authority.canReview(user)).thenReturn(id==3);
        }
        when(identity.isConfigured()).thenReturn(true);
        var consent=new ResearchConsentEntity();consent.setUserId(1L);consent.setActive(true);consent.setConsentVersion("test-v1");consent.setSubjectId("subject-test");
        when(consents.findById(1L)).thenReturn(Optional.of(consent));when(consents.findForUpload(1L)).thenReturn(Optional.of(consent));
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(eq(1L),anyLong(),eq("THERAPIST"))).thenReturn(true);
        sample=new ResearchSampleEntity();sample.setId("00000000-0000-4000-8000-000000000001");sample.setParticipantUserId(1L);
        sample.setSubjectId("subject-test");sample.setSchemaVersion(3);sample.setExerciseType("DEFAULT");sample.setExerciseId("99");
        sample.setExpiresAt(Instant.now().plusSeconds(3600));sample.setPayloadJson(ResearchBodyAttemptTest.fixture().toString());
        when(samples.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(samples.findForUpdate(sample.getId())).thenReturn(Optional.of(sample));
        annotation=new ResearchAnnotationEntity();annotation.setSampleId(sample.getId());annotation.setTherapistUserId(2L);
        annotation.setLabel("insufficient_range");annotation.setLabelVersion("body-attempt-label-v1");
        annotation.setActionDefinitionVersion(ResearchBodyAttemptValidator.DEFINITION);annotation.setStatus("SUBMITTED");annotation.setRevision(3);
        when(annotations.findForUpdate(sample.getId())).thenReturn(Optional.of(annotation));
        when(annotations.findById(sample.getId())).thenReturn(Optional.of(annotation));
        var policy=new ResearchRetentionPolicyEntity();policy.setPolicyVersion("fake-policy");policy.setRetentionDays(1);
        when(retention.currentPolicy()).thenReturn(Optional.of(policy));
    }
    @ParameterizedTest @CsvSource({
        "APPROVE,APPROVED,ACTIVE","RETURN,RETURNED,ACTIVE",
        "REJECT,RETURNED,REJECTED","NEEDS_RESAMPLE,RETURNED,NEEDS_RESAMPLE"})
    void independentDecisionsSnapshotRevisionAndDisposition(String decision,String status,String disposition) {
        var result=service.reviewLabel(3L,"token",sample.getId(),decision,"synthetic review","LOW_QUALITY",3);
        assertThat(result.status()).isEqualTo(status);assertThat(result.revision()).isEqualTo(4);
        assertThat(sample.getDisposition()).isEqualTo(disposition);
        assertThat(annotation.getLabel()).isEqualTo("insufficient_range"); // wrong-but-valid is approvable
        var captured=org.mockito.ArgumentCaptor.forClass(ResearchAnnotationRevisionEntity.class);
        verify(revisions).save(captured.capture());
        assertThat(captured.getValue().getDisposition()).isEqualTo(disposition);
        assertThat(result.reviewerUserId()).isEqualTo(3L);
        assertThat(result.reviewedAt()).isNotNull();
    }
    @Test void selfReviewForbiddenEvenWhenManager() {
        assertThatThrownBy(()->service.reviewLabel(2L,"token",sample.getId(),"APPROVE","",null,3))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
    @Test void staleAndLockedRevisionRejected() {
        assertThatThrownBy(()->service.reviewLabel(3L,"token",sample.getId(),"APPROVE","",null,2)).hasMessageContaining("409");
        annotation.setStatus("APPROVED");
        assertThatThrownBy(()->service.label(2L,"token",sample.getId(),"meets_requirement","","body-attempt-label-v1",ResearchBodyAttemptValidator.DEFINITION,3))
            .hasMessageContaining("409");
    }
    @Test void unauthorizedOrUnboundTherapistForbidden() {
        assertThatThrownBy(()->service.detail(4L,"token",sample.getId())).hasMessageContaining("403");
        when(bindings.existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(1L,3L,"THERAPIST")).thenReturn(false);
        assertThatThrownBy(()->service.reviewLabel(3L,"token",sample.getId(),"APPROVE","",null,3)).hasMessageContaining("403");
    }
    @Test void draftSubmitAndReturnedCorrectionKeepOriginalPayload() {
        String original=sample.getPayloadJson();annotation.setStatus("RETURNED");
        var draft=service.label(2L,"token",sample.getId(),"trunk_compensation","fake note","body-attempt-label-v1",ResearchBodyAttemptValidator.DEFINITION,3);
        assertThat(draft.status()).isEqualTo("DRAFT");
        assertThat(service.submitLabel(2L,"token",sample.getId(),4).status()).isEqualTo("SUBMITTED");
        assertThat(sample.getPayloadJson()).isEqualTo(original);
    }
    @Test void resampleCreatesNewSampleAndCannotLinkAnotherOwner() {
        sample.setDisposition("NEEDS_RESAMPLE");
        var payload=ResearchBodyAttemptTest.fixture();payload.put("resampleOfSampleId",sample.getId());
        when(samples.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        var uploaded=service.upload(1L,"token",payload);
        assertThat(uploaded.id()).isNotEqualTo(sample.getId());assertThat(uploaded.resampleOfSampleId()).isEqualTo(sample.getId());
        assertThat(sample.getDisposition()).isEqualTo("NEEDS_RESAMPLE");
        sample.setParticipantUserId(999L);
        assertThatThrownBy(()->service.upload(1L,"token",payload)).hasMessageContaining("403");
    }
    @Test void v3RequiresRevisionAndReasonOnResample() {
        assertThatThrownBy(()->service.reviewLabel(3L,"token",sample.getId(),"APPROVE","",null,null)).hasMessageContaining("409");
        assertThatThrownBy(()->service.reviewLabel(3L,"token",sample.getId(),"NEEDS_RESAMPLE","note",null,3)).hasMessageContaining("400");
    }
}
