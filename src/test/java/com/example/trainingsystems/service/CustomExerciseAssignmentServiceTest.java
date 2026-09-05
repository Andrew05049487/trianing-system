package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.CustomExerciseAssignmentDto;
import com.example.trainingsystems.dto.CustomRehabExerciseDto;
import com.example.trainingsystems.entity.CustomExerciseAssignmentEntity;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class CustomExerciseAssignmentServiceTest {
    private static final String TOKEN = "signed-token";

    @Mock
    private CustomExerciseAssignmentRepository assignmentRepository;

    @Mock
    private CustomRehabExerciseRepository exerciseRepository;

    @Mock
    private UserBindingRepository bindingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomExerciseIdentityService identityService;

    @Mock
    private CustomRehabExerciseService exerciseService;

    private CustomExerciseAssignmentService service;
    private User therapist;
    private User patient;
    private CustomRehabExerciseEntity exercise;

    @BeforeEach
    void setUp() {
        service = new CustomExerciseAssignmentService(
            assignmentRepository,
            exerciseRepository,
            bindingRepository,
            userRepository,
            identityService,
            exerciseService
        );
        therapist = user(7L, "THERAPIST", "Therapist");
        patient = user(15L, "PATIENT", "Patient");
        exercise = exercise("custom_1", therapist);
        when(identityService.isConfigured()).thenReturn(true);
    }

    @Test
    void therapistCanAssignOwnExerciseToBoundPatient() {
        authenticate(therapist);
        ownExercise();
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(true);
        when(assignmentRepository.findByCustomExercise_IdAndPatient_Id(
            "custom_1",
            15L
        )).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(call -> {
            CustomExerciseAssignmentEntity assignment = call.getArgument(0);
            assignment.setId(101L);
            assignment.beforeSave();
            return assignment;
        });

        CustomExerciseAssignmentDto result = service.assign(
            "custom_1",
            15L,
            7L,
            TOKEN
        );

        assertThat(result.getAssignmentId()).isEqualTo(101L);
        assertThat(result.getExerciseId()).isEqualTo("custom_1");
        assertThat(result.getPatientId()).isEqualTo(15L);
        assertThat(result.getTherapistId()).isEqualTo(7L);
    }

    @Test
    void duplicateAssignmentReusesExistingRow() {
        authenticate(therapist);
        ownExercise();
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(true);
        CustomExerciseAssignmentEntity existing = assignment(
            101L,
            exercise,
            therapist,
            patient
        );
        when(assignmentRepository.findByCustomExercise_IdAndPatient_Id(
            "custom_1",
            15L
        )).thenReturn(Optional.of(existing));
        when(assignmentRepository.save(existing)).thenReturn(existing);

        CustomExerciseAssignmentDto result = service.assign(
            "custom_1",
            15L,
            7L,
            TOKEN
        );

        assertThat(result.getAssignmentId()).isEqualTo(101L);
        verify(assignmentRepository).save(existing);
    }

    @Test
    void therapistCannotAssignAnotherTherapistsExercise() {
        authenticate(therapist);
        when(exerciseRepository.findByIdAndCreatedByTherapist_Id(
            "custom_1",
            7L
        )).thenReturn(Optional.empty());
        when(exerciseRepository.existsById("custom_1")).thenReturn(true);

        assertStatus(
            () -> service.assign("custom_1", 15L, 7L, TOKEN),
            HttpStatus.FORBIDDEN
        );
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void therapistCannotAssignUnboundPatient() {
        authenticate(therapist);
        ownExercise();
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(false);

        assertStatus(
            () -> service.assign("custom_1", 15L, 7L, TOKEN),
            HttpStatus.FORBIDDEN
        );
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void therapistPatientListUsesOnlyTherapistRelationship() {
        authenticate(therapist);
        UserBinding binding = new UserBinding();
        binding.setPatient(patient);
        binding.setLinkedUser(therapist);
        binding.setRelationship("THERAPIST");
        when(bindingRepository
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(7L, "THERAPIST"))
            .thenReturn(List.of(binding));

        assertThat(service.getAssignablePatients(7L, TOKEN))
            .extracting(item -> item.getPatientId())
            .containsExactly(15L);
    }

    @Test
    void patientListsOnlyOwnAssignedExercises() throws Exception {
        authenticate(patient);
        CustomExerciseAssignmentEntity assignment = assignment(
            101L,
            exercise,
            therapist,
            patient
        );
        CustomRehabExerciseDto dto = new CustomRehabExerciseDto();
        dto.setId("custom_1");
        dto.setPoseMeasurementRules(new ObjectMapper().readTree("""
            [{"measurement":"LEFT_ELBOW_ANGLE","targetAngleDegrees":90,"toleranceDegrees":10}]
            """));
        when(assignmentRepository.findAllByPatient_IdAndActiveTrue(
            eq(15L),
            any(Sort.class)
        )).thenReturn(List.of(assignment));
        when(exerciseService.toDto(exercise)).thenReturn(dto);

        List<CustomRehabExerciseDto> result =
            service.getPatientExercises(15L, TOKEN);
        assertThat(result).extracting(CustomRehabExerciseDto::getId)
            .containsExactly("custom_1");
        assertThat(result.get(0).getPoseMeasurementRules())
            .isEqualTo(dto.getPoseMeasurementRules());
        verify(assignmentRepository).findAllByPatient_IdAndActiveTrue(
            eq(15L),
            any(Sort.class)
        );
    }

    @Test
    void patientCannotUseAnotherPatientsIdentityToken() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(identityService.isValid(patient, "other-token")).thenReturn(false);

        assertStatus(
            () -> service.getPatientExercises(15L, "other-token"),
            HttpStatus.FORBIDDEN
        );
        verify(assignmentRepository, never())
            .findAllByPatient_IdAndActiveTrue(any(), any());
    }

    @Test
    void patientCanGetAssignedDetailButNotUnassignedExercise() {
        authenticate(patient);
        CustomExerciseAssignmentEntity assignment = assignment(
            101L,
            exercise,
            therapist,
            patient
        );
        CustomRehabExerciseDto dto = new CustomRehabExerciseDto();
        dto.setId("custom_1");
        when(assignmentRepository
            .findByCustomExercise_IdAndPatient_IdAndActiveTrue(
                "custom_1",
                15L
            )).thenReturn(Optional.of(assignment));
        when(exerciseService.toDto(exercise)).thenReturn(dto);

        assertThat(service.getPatientExercise("custom_1", 15L, TOKEN).getId())
            .isEqualTo("custom_1");

        when(assignmentRepository
            .findByCustomExercise_IdAndPatient_IdAndActiveTrue(
                "custom_2",
                15L
            )).thenReturn(Optional.empty());
        assertStatus(
            () -> service.getPatientExercise("custom_2", 15L, TOKEN),
            HttpStatus.NOT_FOUND
        );
    }

    @Test
    void cancelAssignmentDeletesVisibilityRow() {
        authenticate(therapist);
        ownExercise();
        CustomExerciseAssignmentEntity assignment = assignment(
            101L,
            exercise,
            therapist,
            patient
        );
        when(assignmentRepository.findByCustomExercise_IdAndPatient_Id(
            "custom_1",
            15L
        )).thenReturn(Optional.of(assignment));

        service.unassign("custom_1", 15L, 7L, TOKEN);

        verify(assignmentRepository).delete(assignment);
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private void ownExercise() {
        when(exerciseRepository.findByIdAndCreatedByTherapist_Id(
            "custom_1",
            7L
        )).thenReturn(Optional.of(exercise));
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

    private CustomRehabExerciseEntity exercise(String id, User owner) {
        CustomRehabExerciseEntity entity = new CustomRehabExerciseEntity();
        entity.setId(id);
        entity.setName("Shoulder");
        entity.setDescription("Slowly");
        entity.setCreatedByTherapist(owner);
        entity.setUpdatedAt(Instant.parse("2026-09-04T01:02:04Z"));
        return entity;
    }

    private CustomExerciseAssignmentEntity assignment(
        Long id,
        CustomRehabExerciseEntity assignedExercise,
        User assignedTherapist,
        User assignedPatient
    ) {
        CustomExerciseAssignmentEntity assignment =
            new CustomExerciseAssignmentEntity();
        assignment.setId(id);
        assignment.setCustomExercise(assignedExercise);
        assignment.setAssignedByTherapist(assignedTherapist);
        assignment.setPatient(assignedPatient);
        assignment.setAssignedAt(Instant.parse("2026-09-04T01:02:05Z"));
        assignment.setActive(true);
        return assignment;
    }
}
