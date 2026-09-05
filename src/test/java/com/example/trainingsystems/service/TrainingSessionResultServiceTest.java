package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.TrainingSessionResultDto;
import com.example.trainingsystems.dto.TrainingSessionResultRequest;
import com.example.trainingsystems.entity.CustomExerciseAssignmentEntity;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.TrainingSessionResultEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.ExerciseRepository;
import com.example.trainingsystems.repository.TrainingSessionResultRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingSessionResultServiceTest {
    private static final String TOKEN = "signed-token";
    private static final String SESSION = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Mock TrainingSessionResultRepository resultRepository;
    @Mock UserRepository userRepository;
    @Mock ExerciseRepository defaultExerciseRepository;
    @Mock CustomRehabExerciseRepository customExerciseRepository;
    @Mock ExerciseAssignmentRepository defaultAssignmentRepository;
    @Mock CustomExerciseAssignmentRepository customAssignmentRepository;
    @Mock UserBindingRepository bindingRepository;
    @Mock CustomExerciseIdentityService identityService;

    private TrainingSessionResultService service;
    private User patient;

    @BeforeEach
    void setUp() {
        service = new TrainingSessionResultService(
            resultRepository,
            userRepository,
            defaultExerciseRepository,
            customExerciseRepository,
            defaultAssignmentRepository,
            customAssignmentRepository,
            bindingRepository,
            identityService
        );
        patient = user(15L, "PATIENT");
    }

    @Test
    void patientSavesAssignedCustomCompletionWithDeterministicScore() {
        authenticate(patient);
        when(resultRepository.findBySessionId(SESSION)).thenReturn(Optional.empty());
        when(customAssignmentRepository
            .findByCustomExercise_IdAndPatient_IdAndActiveTrue("custom-1", 15L))
            .thenReturn(Optional.of(new CustomExerciseAssignmentEntity()));
        CustomRehabExerciseEntity exercise = new CustomRehabExerciseEntity();
        exercise.setId("custom-1");
        exercise.setName("手肘訓練");
        when(customExerciseRepository.findById("custom-1"))
            .thenReturn(Optional.of(exercise));
        when(resultRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        TrainingSessionResultDto result = service.save(15L, TOKEN, request());

        assertThat(result.getExerciseName()).isEqualTo("手肘訓練");
        assertThat(result.getScore()).isEqualByComparingTo("100.00");
        assertThat(result.getCompletedAt()).isAfterOrEqualTo(result.getStartedAt());
        verify(resultRepository).saveAndFlush(any());
    }

    @Test
    void sameSessionIsIdempotentAndDoesNotInsertAgain() {
        authenticate(patient);
        TrainingSessionResultEntity existing = result(patient);
        when(resultRepository.findBySessionId(SESSION)).thenReturn(Optional.of(existing));

        TrainingSessionResultDto response = service.save(15L, TOKEN, request());

        assertThat(response.getSessionId()).isEqualTo(SESSION);
        verify(resultRepository, never()).saveAndFlush(any());
    }

    @Test
    void sessionOwnedByAnotherPatientIsRejected() {
        authenticate(patient);
        when(resultRepository.findBySessionId(SESSION))
            .thenReturn(Optional.of(result(user(99L, "PATIENT"))));

        assertStatus(() -> service.save(15L, TOKEN, request()), HttpStatus.FORBIDDEN);
    }

    @Test
    void unassignedExerciseCannotBeSubmitted() {
        authenticate(patient);
        when(resultRepository.findBySessionId(SESSION)).thenReturn(Optional.empty());
        when(customAssignmentRepository
            .findByCustomExercise_IdAndPatient_IdAndActiveTrue("custom-1", 15L))
            .thenReturn(Optional.empty());

        assertStatus(() -> service.save(15L, TOKEN, request()), HttpStatus.FORBIDDEN);
        verify(resultRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidOrIncompletePayloadIsRejected() {
        authenticate(patient);
        TrainingSessionResultRequest request = request();
        request.setCompletedReps(5);

        assertStatus(() -> service.save(15L, TOKEN, request), HttpStatus.BAD_REQUEST);
        verify(resultRepository, never()).saveAndFlush(any());
    }

    @Test
    void invalidIdentityCannotSaveOrRead() {
        when(userRepository.findById(15L)).thenReturn(Optional.of(patient));
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.isValid(patient, "bad")).thenReturn(false);

        assertStatus(() -> service.save(15L, "bad", request()), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.getMine(15L, "bad"), HttpStatus.FORBIDDEN);
    }

    @Test
    void therapistReadsOnlyBoundPatientHistory() {
        User therapist = user(7L, "THERAPIST");
        authenticate(therapist);
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                15L, 7L, "THERAPIST"))
            .thenReturn(true);
        when(resultRepository.findAllByPatient_IdOrderByCompletedAtDesc(15L))
            .thenReturn(List.of(result(patient)));

        assertThat(service.getForTherapist(7L, TOKEN, 15L)).hasSize(1);

        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                99L, 7L, "THERAPIST"))
            .thenReturn(false);
        assertStatus(
            () -> service.getForTherapist(7L, TOKEN, 99L),
            HttpStatus.FORBIDDEN
        );
    }

    private void authenticate(User user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.isValid(user, TOKEN)).thenReturn(true);
    }

    private TrainingSessionResultRequest request() {
        TrainingSessionResultRequest request = new TrainingSessionResultRequest();
        request.setSessionId(SESSION);
        request.setExerciseType("CUSTOM");
        request.setExerciseId("custom-1");
        request.setCompletedSets(2);
        request.setCompletedReps(6);
        request.setTargetSets(2);
        request.setTargetReps(3);
        request.setDurationSeconds(30L);
        request.setCompletionStatus("COMPLETED");
        return request;
    }

    private TrainingSessionResultEntity result(User owner) {
        TrainingSessionResultEntity result = new TrainingSessionResultEntity();
        result.setSessionId(SESSION);
        result.setPatient(owner);
        result.setExerciseType("CUSTOM");
        result.setExerciseId("custom-1");
        result.setExerciseName("手肘訓練");
        result.setCompletedSets(2);
        result.setCompletedReps(6);
        result.setTargetSets(2);
        result.setTargetReps(3);
        result.setStartedAt(Instant.parse("2026-09-05T01:00:00Z"));
        result.setCompletedAt(Instant.parse("2026-09-05T01:00:30Z"));
        result.setDurationSeconds(30L);
        result.setCompletionStatus("COMPLETED");
        result.setScore(new BigDecimal("100.00"));
        return result;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(CustomExerciseApiException.class)
            .extracting(error -> ((CustomExerciseApiException) error).getStatus())
            .isEqualTo(status);
    }
}
