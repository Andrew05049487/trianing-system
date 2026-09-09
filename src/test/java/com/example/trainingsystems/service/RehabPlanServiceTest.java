package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.RehabPlanItemDto;
import com.example.trainingsystems.dto.RehabPlanRequest;
import com.example.trainingsystems.dto.RehabPlanResponse;
import com.example.trainingsystems.entity.RehabPlanEntity;
import com.example.trainingsystems.entity.RehabPlanItemEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.RehabPlanRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RehabPlanServiceTest {
    private static final Long THERAPIST_ID = 7L;
    private static final Long PATIENT_A_ID = 15L;
    private static final Long PATIENT_B_ID = 16L;
    private static final String TOKEN = "signed-token";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    @Mock
    private RehabPlanRepository planRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBindingRepository bindingRepository;
    @Mock
    private CustomExerciseIdentityService identityService;

    private RehabPlanService service;
    private User therapist;
    private User patientA;
    private User patientB;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
            Instant.parse("2026-09-09T04:00:00Z"),
            ZoneOffset.UTC
        );
        service = new RehabPlanService(
            planRepository,
            userRepository,
            bindingRepository,
            identityService,
            clock
        );
        therapist = user(THERAPIST_ID, "THERAPIST");
        patientA = user(PATIENT_A_ID, "PATIENT");
        patientB = user(PATIENT_B_ID, "PATIENT");
        lenient().when(planRepository.save(any(RehabPlanEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsTodaysPlan() {
        authorizeTherapist(patientA);
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, TODAY))
            .thenReturn(Optional.empty());
        when(planRepository.findByPlanId(planId(PATIENT_A_ID, TODAY)))
            .thenReturn(Optional.empty());

        RehabPlanResponse response = service.savePlan(
            request(PATIENT_A_ID, TODAY, item("ex01", 0, 3, 10, false)),
            THERAPIST_ID,
            TOKEN
        );

        assertThat(response.date()).isEqualTo("2026-09-09");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).done()).isFalse();
    }

    @Test
    void createsFuturePlan() {
        LocalDate future = TODAY.plusDays(1);
        authorizeTherapist(patientA);
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, future))
            .thenReturn(Optional.empty());
        when(planRepository.findByPlanId(planId(PATIENT_A_ID, future)))
            .thenReturn(Optional.empty());

        RehabPlanResponse response = service.savePlan(
            request(PATIENT_A_ID, future, item("ex02", 0, 2, 8, false)),
            THERAPIST_ID,
            TOKEN
        );

        assertThat(response.date()).isEqualTo("2026-09-10");
    }

    @Test
    void rejectsCreatingPastPlan() {
        authenticate(therapist);

        assertStatus(
            () -> service.savePlan(
                request(
                    PATIENT_A_ID,
                    TODAY.minusDays(1),
                    item("ex01", 0, 3, 10, false)
                ),
                THERAPIST_ID,
                TOKEN
            ),
            HttpStatus.CONFLICT
        );
        verify(userRepository, never()).findByIdForUpdate(anyLong());
        verify(planRepository, never()).save(any());
    }

    @Test
    void modifiesTodaysPlanItem() {
        RehabPlanEntity plan = plan(patientA, TODAY, false, "ex01");
        authorizeTherapistRead(patientA);
        when(planRepository.findForUpdate(
            PATIENT_A_ID,
            plan.getPlanId()
        )).thenReturn(Optional.of(plan));

        RehabPlanResponse response = service.updatePlanItem(
            PATIENT_A_ID.toString(),
            plan.getPlanId(),
            "ex01",
            item("ex01", 1, 4, 12, false),
            THERAPIST_ID,
            TOKEN
        );

        assertThat(response.items().get(0).order()).isEqualTo(1);
        assertThat(response.items().get(0).sets()).isEqualTo(4);
        assertThat(response.items().get(0).repsPerSet()).isEqualTo(12);
    }

    @Test
    void modifiesFuturePlanItem() {
        LocalDate future = TODAY.plusDays(1);
        RehabPlanEntity plan = plan(patientA, future, false, "ex01");
        authorizeTherapistRead(patientA);
        when(planRepository.findForUpdate(PATIENT_A_ID, plan.getPlanId()))
            .thenReturn(Optional.of(plan));

        RehabPlanResponse response = service.updatePlanItem(
            PATIENT_A_ID.toString(),
            plan.getPlanId(),
            "ex01",
            item("ex01", 0, 5, 15, false),
            THERAPIST_ID,
            TOKEN
        );

        assertThat(response.items().get(0).sets()).isEqualTo(5);
        assertThat(response.items().get(0).repsPerSet()).isEqualTo(15);
    }

    @Test
    void rejectsModifyingPastPlanItem() {
        RehabPlanEntity plan = plan(patientA, TODAY.minusDays(1), false, "ex01");
        authorizeTherapistRead(patientA);
        when(planRepository.findForUpdate(PATIENT_A_ID, plan.getPlanId()))
            .thenReturn(Optional.of(plan));

        assertStatus(
            () -> service.updatePlanItem(
                PATIENT_A_ID.toString(),
                plan.getPlanId(),
                "ex01",
                item("ex01", 0, 3, 10, true),
                THERAPIST_ID,
                TOKEN
            ),
            HttpStatus.CONFLICT
        );
        verify(planRepository, never()).save(any());
    }

    @Test
    void getsPastPlanWithHistoricalDoneState() {
        LocalDate past = TODAY.minusDays(2);
        RehabPlanEntity plan = plan(patientA, past, true, "ex01");
        authorizePatient(patientA);
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, past))
            .thenReturn(Optional.of(plan));

        RehabPlanResponse response = service.getPlan(
            PATIENT_A_ID.toString(),
            past,
            PATIENT_A_ID,
            TOKEN
        );

        assertThat(response.items().get(0).done()).isTrue();
    }

    @Test
    void samePatientAndDateUpdatesExistingPlanWithoutCreatingAnother() {
        RehabPlanEntity existing = plan(patientA, TODAY, true, "ex01");
        authorizeTherapist(patientA);
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, TODAY))
            .thenReturn(Optional.of(existing));
        when(planRepository.findByPlanId(existing.getPlanId()))
            .thenReturn(Optional.of(existing));

        service.savePlan(
            request(PATIENT_A_ID, TODAY, item("ex01", 0, 6, 20, false)),
            THERAPIST_ID,
            TOKEN
        );

        ArgumentCaptor<RehabPlanEntity> captor =
            ArgumentCaptor.forClass(RehabPlanEntity.class);
        verify(planRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(existing.getItems()).hasSize(1);
        assertThat(existing.getItems().get(0).isDone()).isTrue();
    }

    @Test
    void patientsRemainIsolatedOnTheSameDate() {
        authorizeTherapist(patientA);
        when(userRepository.findByIdForUpdate(PATIENT_B_ID))
            .thenReturn(Optional.of(patientB));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                PATIENT_B_ID,
                THERAPIST_ID,
                "THERAPIST"
            )).thenReturn(true);
        when(planRepository.findByPatient_IdAndPlanDate(anyLong(), any()))
            .thenReturn(Optional.empty());
        when(planRepository.findByPlanId(anyString()))
            .thenReturn(Optional.empty());

        service.savePlan(
            request(PATIENT_A_ID, TODAY, item("ex01", 0, 3, 10, false)),
            THERAPIST_ID,
            TOKEN
        );
        service.savePlan(
            request(PATIENT_B_ID, TODAY, item("ex03", 0, 2, 8, false)),
            THERAPIST_ID,
            TOKEN
        );

        ArgumentCaptor<RehabPlanEntity> captor =
            ArgumentCaptor.forClass(RehabPlanEntity.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(plan -> plan.getPatient().getId())
            .containsExactly(PATIENT_A_ID, PATIENT_B_ID);
    }

    @Test
    void patchDonePersistsForTheNextGet() {
        RehabPlanEntity plan = plan(patientA, TODAY, false, "ex01");
        authorizePatient(patientA);
        when(planRepository.findForUpdate(PATIENT_A_ID, plan.getPlanId()))
            .thenReturn(Optional.of(plan));
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, TODAY))
            .thenReturn(Optional.of(plan));

        service.updatePlanItem(
            PATIENT_A_ID.toString(),
            plan.getPlanId(),
            "ex01",
            item("ex01", 0, 3, 10, true),
            PATIENT_A_ID,
            TOKEN
        );
        RehabPlanResponse reloaded = service.getPlan(
            PATIENT_A_ID.toString(),
            TODAY,
            PATIENT_A_ID,
            TOKEN
        );

        assertThat(reloaded.items().get(0).done()).isTrue();
    }

    @Test
    void patientAPatchCannotChangePatientB() {
        RehabPlanEntity patientAPlan = plan(patientA, TODAY, false, "ex01");
        RehabPlanEntity patientBPlan = plan(patientB, TODAY, false, "ex01");
        authorizePatient(patientA);
        when(planRepository.findForUpdate(PATIENT_A_ID, patientAPlan.getPlanId()))
            .thenReturn(Optional.of(patientAPlan));

        service.updatePlanItem(
            PATIENT_A_ID.toString(),
            patientAPlan.getPlanId(),
            "ex01",
            item("ex01", 0, 3, 10, true),
            PATIENT_A_ID,
            TOKEN
        );

        assertThat(patientAPlan.getItems().get(0).isDone()).isTrue();
        assertThat(patientBPlan.getItems().get(0).isDone()).isFalse();
        verify(planRepository).findForUpdate(
            PATIENT_A_ID,
            patientAPlan.getPlanId()
        );
    }

    @Test
    void missingPlanGetReturnsNotFound() {
        authorizePatient(patientA);
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, TODAY))
            .thenReturn(Optional.empty());

        assertStatus(
            () -> service.getPlan(
                PATIENT_A_ID.toString(),
                TODAY,
                PATIENT_A_ID,
                TOKEN
            ),
            HttpStatus.NOT_FOUND
        );
    }

    @Test
    void wholePlanUpdatePreservesDoneAndRemovesOnlyOmittedItems() {
        RehabPlanEntity existing = plan(patientA, TODAY, true, "ex01");
        existing.addItem(entityItem("ex02", 1, false));
        authorizeTherapist(patientA);
        when(planRepository.findByPatient_IdAndPlanDate(PATIENT_A_ID, TODAY))
            .thenReturn(Optional.of(existing));
        when(planRepository.findByPlanId(existing.getPlanId()))
            .thenReturn(Optional.of(existing));

        RehabPlanResponse response = service.savePlan(
            new RehabPlanRequest(
                PATIENT_A_ID.toString(),
                existing.getPlanId(),
                "therapist",
                "2026-09-09T00:00:00.000",
                "stroke",
                List.of(
                    item("ex01", 1, 4, 12, false),
                    item("ex03", 0, 2, 8, true)
                )
            ),
            THERAPIST_ID,
            TOKEN
        );

        assertThat(response.items())
            .extracting(RehabPlanItemDto::exerciseId)
            .containsExactly("ex03", "ex01");
        assertThat(response.items().stream()
            .filter(item -> item.exerciseId().equals("ex01"))
            .findFirst().orElseThrow().done()).isTrue();
        assertThat(response.items().stream()
            .filter(item -> item.exerciseId().equals("ex03"))
            .findFirst().orElseThrow().done()).isFalse();
    }

    private void authorizeTherapist(User patient) {
        authenticate(therapist);
        when(userRepository.findByIdForUpdate(patient.getId()))
            .thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patient.getId(),
                THERAPIST_ID,
                "THERAPIST"
            )).thenReturn(true);
    }

    private void authorizeTherapistRead(User patient) {
        authenticate(therapist);
        when(userRepository.findById(patient.getId()))
            .thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patient.getId(),
                THERAPIST_ID,
                "THERAPIST"
            )).thenReturn(true);
    }

    private void authorizePatient(User patient) {
        authenticate(patient);
        when(userRepository.findById(patient.getId()))
            .thenReturn(Optional.of(patient));
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private RehabPlanRequest request(
        Long patientId,
        LocalDate date,
        RehabPlanItemDto... items
    ) {
        return new RehabPlanRequest(
            patientId.toString(),
            planId(patientId, date),
            "therapist",
            date.toString(),
            "stroke",
            List.of(items)
        );
    }

    private RehabPlanItemDto item(
        String exerciseId,
        int order,
        int sets,
        int reps,
        boolean done
    ) {
        return new RehabPlanItemDto(exerciseId, order, sets, reps, done);
    }

    private RehabPlanEntity plan(
        User patient,
        LocalDate date,
        boolean done,
        String exerciseId
    ) {
        RehabPlanEntity plan = new RehabPlanEntity();
        plan.setId(patient.getId() * 1000 + date.getDayOfMonth());
        plan.setPatient(patient);
        plan.setPlanId(planId(patient.getId(), date));
        plan.setCreatedBy("therapist");
        plan.setPlanDate(date);
        plan.setCondition("stroke");
        plan.addItem(entityItem(exerciseId, 0, done));
        return plan;
    }

    private RehabPlanItemEntity entityItem(
        String exerciseId,
        int order,
        boolean done
    ) {
        RehabPlanItemEntity item = new RehabPlanItemEntity();
        item.setExerciseId(exerciseId);
        item.setItemOrder(order);
        item.setSets(3);
        item.setRepsPerSet(10);
        item.setDone(done);
        return item;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName(role + " " + id);
        return user;
    }

    private String planId(Long patientId, LocalDate date) {
        return "plan_" + patientId + "_" + date.toString().replace("-", "");
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(RehabPlanApiException.class)
            .extracting(error -> ((RehabPlanApiException) error).getStatus())
            .isEqualTo(status);
    }
}
