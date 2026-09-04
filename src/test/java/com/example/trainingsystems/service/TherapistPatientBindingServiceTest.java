package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.TherapistPatientDto;
import com.example.trainingsystems.entity.CustomExerciseAssignmentEntity;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TherapistPatientBindingServiceTest {
    private static final String TOKEN = "signed-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBindingRepository bindingRepository;

    @Mock
    private ExerciseAssignmentRepository defaultAssignmentRepository;

    @Mock
    private CustomExerciseAssignmentRepository customAssignmentRepository;

    @Mock
    private CustomExerciseIdentityService identityService;

    @Mock
    private CustomRehabExerciseRepository customExerciseRepository;

    @Mock
    private CustomRehabExerciseService customExerciseService;

    private TherapistPatientBindingService service;
    private User therapist;
    private User otherTherapist;
    private User patient;

    @BeforeEach
    void setUp() {
        service = new TherapistPatientBindingService(
            userRepository,
            bindingRepository,
            defaultAssignmentRepository,
            customAssignmentRepository,
            identityService
        );
        therapist = user(7L, "THERAPIST", "治療師", "doctor@example.com");
        otherTherapist = user(
            8L,
            "THERAPIST",
            "其他治療師",
            "other@example.com"
        );
        patient = user(15L, "PATIENT", "王小明", "patient@example.com");
        patient.setBindingCode("ABC12345");
        when(identityService.isConfigured()).thenReturn(true);
    }

    @Test
    void therapistCanBindPatientByValidCode() {
        authenticate(therapist);
        patientCodeFinds(patient);
        when(bindingRepository.existsByPatient_IdAndLinkedUser_Id(15L, 7L))
            .thenReturn(false);
        when(bindingRepository.save(any())).thenAnswer(call -> {
            UserBinding binding = call.getArgument(0);
            binding.setId(101L);
            binding.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 30));
            return binding;
        });

        TherapistPatientDto result = service.bindPatient(
            " abc12345 ",
            7L,
            TOKEN
        );

        assertThat(result.patientId()).isEqualTo(15L);
        assertThat(result.patientEmail()).isEqualTo("patient@example.com");
        assertThat(result.relationship()).isEqualTo("THERAPIST");
    }

    @Test
    void invalidCodeIsRejected() {
        authenticate(therapist);
        when(userRepository.findByBindingCode("MISSING"))
            .thenReturn(Optional.empty());

        assertStatus(
            () -> service.lookupPatient("missing", 7L, TOKEN),
            HttpStatus.NOT_FOUND
        );
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void nonPatientCodeIsRejected() {
        authenticate(therapist);
        when(userRepository.findByBindingCode("THERAPIST8"))
            .thenReturn(Optional.of(otherTherapist));

        assertStatus(
            () -> service.bindPatient("therapist8", 7L, TOKEN),
            HttpStatus.BAD_REQUEST
        );
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void duplicateBindingIsPrevented() {
        authenticate(therapist);
        patientCodeFinds(patient);
        when(bindingRepository.existsByPatient_IdAndLinkedUser_Id(15L, 7L))
            .thenReturn(true);

        assertStatus(
            () -> service.bindPatient("ABC12345", 7L, TOKEN),
            HttpStatus.CONFLICT
        );
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void therapistListsOnlyOwnTherapistPatients() {
        authenticate(therapist);
        UserBinding binding = binding(patient, therapist, "THERAPIST");
        when(bindingRepository
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(7L, "THERAPIST"))
            .thenReturn(List.of(binding));

        assertThat(service.getPatients(7L, TOKEN))
            .extracting(TherapistPatientDto::patientId)
            .containsExactly(15L);
        verify(bindingRepository)
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(7L, "THERAPIST");
    }

    @Test
    void therapistCannotListUsingAnotherIdentity() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(otherTherapist));
        when(identityService.isValid(otherTherapist, TOKEN)).thenReturn(false);

        assertStatus(
            () -> service.getPatients(8L, TOKEN),
            HttpStatus.FORBIDDEN
        );
        verify(bindingRepository, never())
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(any(), any());
    }

    @Test
    void therapistCanUnbindOwnPatientAndAssignmentsAreCancelled() {
        authenticate(therapist);
        UserBinding binding = binding(patient, therapist, "THERAPIST");
        when(bindingRepository
            .findByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(Optional.of(binding));

        service.unbindPatient(15L, 7L, TOKEN);

        verify(defaultAssignmentRepository)
            .deleteAllByPatient_IdAndAssignedByTherapist_Id(15L, 7L);
        verify(customAssignmentRepository)
            .deleteAllByPatient_IdAndAssignedByTherapist_Id(15L, 7L);
        verify(bindingRepository).delete(binding);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void therapistCannotUnbindAnotherTherapistsBinding() {
        authenticate(therapist);
        when(bindingRepository
            .findByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(Optional.empty());

        assertStatus(
            () -> service.unbindPatient(15L, 7L, TOKEN),
            HttpStatus.NOT_FOUND
        );
        verify(defaultAssignmentRepository, never())
            .deleteAllByPatient_IdAndAssignedByTherapist_Id(any(), any());
        verify(customAssignmentRepository, never())
            .deleteAllByPatient_IdAndAssignedByTherapist_Id(any(), any());
        verify(bindingRepository, never()).delete(any());
    }

    @Test
    void familyRelationshipIsUnaffectedByTherapistUnbind() {
        authenticate(therapist);
        when(bindingRepository
            .findByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenReturn(Optional.empty());

        assertStatus(
            () -> service.unbindPatient(15L, 7L, TOKEN),
            HttpStatus.NOT_FOUND
        );
        verify(bindingRepository, never()).delete(any());
    }

    @Test
    void assignmentAuthorizationWorksImmediatelyAfterBinding() {
        authenticate(therapist);
        patientCodeFinds(patient);
        AtomicBoolean saved = new AtomicBoolean(false);
        when(bindingRepository.existsByPatient_IdAndLinkedUser_Id(15L, 7L))
            .thenReturn(false);
        when(bindingRepository.save(any())).thenAnswer(call -> {
            UserBinding binding = call.getArgument(0);
            saved.set(
                binding.getPatient().getId().equals(15L)
                    && binding.getLinkedUser().getId().equals(7L)
                    && "THERAPIST".equals(binding.getRelationship())
            );
            return binding;
        });
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L,
                7L,
                "THERAPIST"
            )).thenAnswer(call -> saved.get());

        service.bindPatient("ABC12345", 7L, TOKEN);

        CustomRehabExerciseEntity exercise = new CustomRehabExerciseEntity();
        exercise.setId("custom_1");
        exercise.setName("肩部活動");
        exercise.setDescription("");
        exercise.setCreatedByTherapist(therapist);
        when(customExerciseRepository.findByIdAndCreatedByTherapist_Id(
            "custom_1",
            7L
        )).thenReturn(Optional.of(exercise));
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(customAssignmentRepository.findByCustomExercise_IdAndPatient_Id(
            "custom_1",
            15L
        )).thenReturn(Optional.empty());
        when(customAssignmentRepository.save(any())).thenAnswer(call -> {
            CustomExerciseAssignmentEntity assignment = call.getArgument(0);
            assignment.setId(201L);
            assignment.setAssignedAt(Instant.parse("2026-09-04T02:00:00Z"));
            return assignment;
        });
        CustomExerciseAssignmentService assignmentService =
            new CustomExerciseAssignmentService(
                customAssignmentRepository,
                customExerciseRepository,
                bindingRepository,
                userRepository,
                identityService,
                customExerciseService
            );

        assertThat(assignmentService.assign(
            "custom_1",
            15L,
            7L,
            TOKEN
        ).getPatientId()).isEqualTo(15L);
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private void patientCodeFinds(User expected) {
        when(userRepository.findByBindingCode("ABC12345"))
            .thenReturn(Optional.of(expected));
    }

    private void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(CustomExerciseApiException.class)
            .extracting(error -> ((CustomExerciseApiException) error).getStatus())
            .isEqualTo(expected);
    }

    private User user(Long id, String role, String name, String email) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName(name);
        user.setEmail(email);
        return user;
    }

    private UserBinding binding(
        User linkedPatient,
        User linkedTherapist,
        String relationship
    ) {
        UserBinding binding = new UserBinding();
        binding.setId(101L);
        binding.setPatient(linkedPatient);
        binding.setLinkedUser(linkedTherapist);
        binding.setRelationship(relationship);
        binding.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 30));
        return binding;
    }
}
