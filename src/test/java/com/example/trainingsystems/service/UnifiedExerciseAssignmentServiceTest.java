package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AssignableExerciseDto;
import com.example.trainingsystems.dto.CustomExerciseAssignmentDto;
import com.example.trainingsystems.entity.CustomExerciseAssignmentEntity;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.Exercise;
import com.example.trainingsystems.entity.ExerciseAssignmentEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.ExerciseRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnifiedExerciseAssignmentServiceTest {
    private static final String TOKEN = "signed-token";

    @Mock
    private ExerciseAssignmentRepository defaultAssignmentRepository;
    @Mock
    private ExerciseRepository defaultExerciseRepository;
    @Mock
    private CustomExerciseAssignmentRepository customAssignmentRepository;
    @Mock
    private CustomRehabExerciseRepository customExerciseRepository;
    @Mock
    private UserBindingRepository bindingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomExerciseIdentityService identityService;
    @Mock
    private CustomExerciseAssignmentService customAssignmentService;

    private UnifiedExerciseAssignmentService service;
    private User therapist;
    private User patient;
    private Exercise defaultExercise;
    private CustomRehabExerciseEntity customExercise;

    @BeforeEach
    void setUp() {
        service = new UnifiedExerciseAssignmentService(
            defaultAssignmentRepository,
            defaultExerciseRepository,
            customAssignmentRepository,
            customExerciseRepository,
            bindingRepository,
            userRepository,
            identityService,
            customAssignmentService
        );
        therapist = user(7L, "THERAPIST", "Therapist");
        patient = user(15L, "PATIENT", "Patient");
        defaultExercise = defaultExercise(1L, "翻掌訓練");
        customExercise = customExercise("custom_1", therapist);
    }

    @Test
    void therapistSeesDefaultAndOwnedCustomWithAssignmentState() {
        authenticate(therapist);
        bindPatient();
        ExerciseAssignmentEntity defaultAssignment = defaultAssignment();
        CustomExerciseAssignmentEntity customAssignment = customAssignment();
        when(defaultAssignmentRepository
            .findAllByPatient_IdAndAssignedByTherapist_IdAndActiveTrue(15L, 7L))
            .thenReturn(List.of(defaultAssignment));
        when(customAssignmentRepository
            .findAllByPatient_IdAndAssignedByTherapist_IdAndActiveTrue(15L, 7L))
            .thenReturn(List.of(customAssignment));
        when(defaultExerciseRepository.findAll(any(Sort.class)))
            .thenReturn(List.of(defaultExercise));
        when(customExerciseRepository.findAllByCreatedByTherapist_Id(
            eq(7L),
            any(Sort.class)
        )).thenReturn(List.of(customExercise));

        List<AssignableExerciseDto> result = service.getAssignableExercises(
            7L,
            TOKEN,
            15L
        );

        assertThat(result)
            .extracting(AssignableExerciseDto::getType)
            .containsExactly("DEFAULT", "CUSTOM");
        assertThat(result).allMatch(AssignableExerciseDto::isAssigned);
    }

    @Test
    void duplicateDefaultAssignmentReusesExistingRelationship() {
        authenticate(therapist);
        bindPatient();
        when(defaultExerciseRepository.findById(1L))
            .thenReturn(Optional.of(defaultExercise));
        ExerciseAssignmentEntity existing = defaultAssignment();
        when(defaultAssignmentRepository.findByExercise_IdAndPatient_Id(1L, 15L))
            .thenReturn(Optional.of(existing));
        when(defaultAssignmentRepository.save(existing)).thenReturn(existing);

        AssignableExerciseDto result = service.assign(
            "DEFAULT",
            "1",
            15L,
            7L,
            TOKEN
        );

        assertThat(result.isAssigned()).isTrue();
        verify(defaultAssignmentRepository).save(existing);
    }

    @Test
    void therapistCannotAssignDefaultToUnboundPatient() {
        authenticate(therapist);
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(false);

        assertStatus(
            () -> service.assign("DEFAULT", "1", 15L, 7L, TOKEN),
            HttpStatus.FORBIDDEN
        );
        verify(defaultAssignmentRepository, never()).save(any());
    }

    @Test
    void customAssignmentDelegatesToMilestoneSevenService() {
        CustomExerciseAssignmentDto customResult = new CustomExerciseAssignmentDto();
        customResult.setExerciseId("custom_1");
        customResult.setExerciseName("肩部活動");
        customResult.setExerciseDescription("Slowly");
        when(customAssignmentService.assign(
            "custom_1",
            15L,
            7L,
            TOKEN
        )).thenReturn(customResult);

        AssignableExerciseDto result = service.assign(
            "CUSTOM",
            "custom_1",
            15L,
            7L,
            TOKEN
        );

        assertThat(result.getType()).isEqualTo("CUSTOM");
        assertThat(result.isAssigned()).isTrue();
    }

    @Test
    void unassignDefaultDeletesOnlyRelationshipNotSourceExercise() {
        authenticate(therapist);
        bindPatient();
        when(defaultExerciseRepository.existsById(1L)).thenReturn(true);
        ExerciseAssignmentEntity assignment = defaultAssignment();
        when(defaultAssignmentRepository.findByExercise_IdAndPatient_Id(1L, 15L))
            .thenReturn(Optional.of(assignment));

        service.unassign("DEFAULT", "1", 15L, 7L, TOKEN);

        verify(defaultAssignmentRepository).delete(assignment);
        verify(defaultExerciseRepository, never()).delete(any());
    }

    @Test
    void patientSeesOwnAssignedDefaultAndCustomExercises() {
        authenticate(patient);
        when(defaultAssignmentRepository.findAllByPatient_IdAndActiveTrue(
            eq(15L),
            any(Sort.class)
        )).thenReturn(List.of(defaultAssignment()));
        when(customAssignmentRepository.findAllByPatient_IdAndActiveTrue(
            eq(15L),
            any(Sort.class)
        )).thenReturn(List.of(customAssignment()));

        List<AssignableExerciseDto> result = service.getPatientAssignedExercises(
            15L,
            TOKEN
        );

        assertThat(result)
            .extracting(AssignableExerciseDto::getType)
            .containsExactly("DEFAULT", "CUSTOM");
        verify(defaultAssignmentRepository).findAllByPatient_IdAndActiveTrue(
            eq(15L),
            any(Sort.class)
        );
    }

    @Test
    void patientCannotReadUsingAnotherPatientsToken() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.isValid(patient, "other-token")).thenReturn(false);

        assertStatus(
            () -> service.getPatientAssignedExercises(15L, "other-token"),
            HttpStatus.FORBIDDEN
        );
        verify(defaultAssignmentRepository, never())
            .findAllByPatient_IdAndActiveTrue(any(), any());
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private void bindPatient() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(true);
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(CustomExerciseApiException.class)
            .extracting(error -> ((CustomExerciseApiException) error).getStatus())
            .isEqualTo(status);
    }

    private User user(Long id, String role, String name) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName(name);
        return user;
    }

    private Exercise defaultExercise(Long id, String name) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setExerciseName(name);
        exercise.setDescription("Default exercise");
        return exercise;
    }

    private CustomRehabExerciseEntity customExercise(String id, User owner) {
        CustomRehabExerciseEntity exercise = new CustomRehabExerciseEntity();
        exercise.setId(id);
        exercise.setName("肩部活動");
        exercise.setDescription("Slowly");
        exercise.setCreatedByTherapist(owner);
        return exercise;
    }

    private ExerciseAssignmentEntity defaultAssignment() {
        ExerciseAssignmentEntity assignment = new ExerciseAssignmentEntity();
        assignment.setId(101L);
        assignment.setExercise(defaultExercise);
        assignment.setPatient(patient);
        assignment.setAssignedByTherapist(therapist);
        assignment.setAssignedAt(Instant.parse("2026-09-04T01:02:03Z"));
        assignment.setActive(true);
        return assignment;
    }

    private CustomExerciseAssignmentEntity customAssignment() {
        CustomExerciseAssignmentEntity assignment =
            new CustomExerciseAssignmentEntity();
        assignment.setId(201L);
        assignment.setCustomExercise(customExercise);
        assignment.setPatient(patient);
        assignment.setAssignedByTherapist(therapist);
        assignment.setAssignedAt(Instant.parse("2026-09-04T01:02:04Z"));
        assignment.setActive(true);
        return assignment;
    }
}
