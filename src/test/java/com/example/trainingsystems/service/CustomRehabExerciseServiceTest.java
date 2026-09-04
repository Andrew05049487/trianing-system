package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.CustomRehabExerciseDto;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CustomRehabExerciseServiceTest {

    private static final String TOKEN = "signed-token";

    @Mock
    private CustomRehabExerciseRepository exerciseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomExerciseIdentityService identityService;

    private CustomRehabExerciseService service;
    private User therapist;

    @BeforeEach
    void setUp() {
        service = new CustomRehabExerciseService(
            exerciseRepository,
            userRepository,
            new ObjectMapper(),
            identityService
        );
        therapist = user(7L, "THERAPIST");
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(therapist));
        lenient().when(identityService.isConfigured()).thenReturn(true);
        lenient().when(identityService.isValid(therapist, TOKEN)).thenReturn(true);
    }

    @Test
    void savesNewExerciseAndUsesAuthenticatedTherapist() {
        CustomRehabExerciseDto request = validRequest("custom_1");
        request.setCreatedByTherapistId("999");
        when(exerciseRepository.findById("custom_1")).thenReturn(Optional.empty());
        when(exerciseRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        CustomRehabExerciseDto saved = service.save("custom_1", 7L, TOKEN, request);

        assertThat(saved.getId()).isEqualTo("custom_1");
        assertThat(saved.getCreatedByTherapistId()).isEqualTo("7");
        assertThat(saved.getKeyframes()).isEqualTo(request.getKeyframes());
    }

    @Test
    void repeatSaveSameIdUpdatesExistingEntityWithoutChangingOwnerOrCreatedAt() {
        CustomRehabExerciseEntity existing = entity("custom_1", therapist);
        Instant createdAt = existing.getCreatedAt();
        when(exerciseRepository.findById("custom_1")).thenReturn(Optional.of(existing));
        when(exerciseRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        CustomRehabExerciseDto request = validRequest("custom_1");
        request.setName("Updated");

        CustomRehabExerciseDto saved = service.save("custom_1", 7L, TOKEN, request);

        assertThat(saved.getName()).isEqualTo("Updated");
        assertThat(existing.getCreatedAt()).isEqualTo(createdAt);
        assertThat(existing.getCreatedByTherapist()).isSameAs(therapist);
    }

    @Test
    void getsOwnedExercise() {
        when(exerciseRepository.findByIdAndCreatedByTherapist_Id("custom_1", 7L))
            .thenReturn(Optional.of(entity("custom_1", therapist)));

        assertThat(service.get("custom_1", 7L, TOKEN).getId()).isEqualTo("custom_1");
    }

    @Test
    void listsOnlyRepositoryResultsForTherapist() {
        when(exerciseRepository.findAllByCreatedByTherapist_Id(eq(7L), any(Sort.class)))
            .thenReturn(List.of(entity("custom_1", therapist)));

        List<CustomRehabExerciseDto> result = service.getAll(7L, TOKEN);

        assertThat(result).extracting(CustomRehabExerciseDto::getId)
            .containsExactly("custom_1");
    }

    @Test
    void deletesOwnedExercise() {
        CustomRehabExerciseEntity entity = entity("custom_1", therapist);
        when(exerciseRepository.findByIdAndCreatedByTherapist_Id("custom_1", 7L))
            .thenReturn(Optional.of(entity));

        service.delete("custom_1", 7L, TOKEN);

        verify(exerciseRepository).delete(entity);
    }

    @Test
    void wrongTherapistCannotGetExercise() {
        when(exerciseRepository.findByIdAndCreatedByTherapist_Id("custom_1", 7L))
            .thenReturn(Optional.empty());
        when(exerciseRepository.existsById("custom_1")).thenReturn(true);

        assertStatus(
            () -> service.get("custom_1", 7L, TOKEN),
            HttpStatus.FORBIDDEN
        );
    }

    @Test
    void nonTherapistCannotCreate() {
        User patient = user(7L, "PATIENT");
        when(userRepository.findById(7L)).thenReturn(Optional.of(patient));

        assertStatus(
            () -> service.save("custom_1", 7L, TOKEN, validRequest("custom_1")),
            HttpStatus.FORBIDDEN
        );
        verify(exerciseRepository, never()).save(any());
    }

    @Test
    void invalidIdentityTokenIsRejected() {
        when(identityService.isValid(therapist, "wrong-token")).thenReturn(false);

        assertStatus(
            () -> service.getAll(7L, "wrong-token"),
            HttpStatus.FORBIDDEN
        );
    }

    @Test
    void invalidKeyframesAreRejected() throws Exception {
        CustomRehabExerciseDto request = validRequest("custom_1");
        request.setKeyframes(new ObjectMapper().readTree("""
            [
              {"id":"a","time":0,"jointRotations":{}},
              {"id":"b","time":0,"jointRotations":{}}
            ]
            """));

        assertStatus(
            () -> service.save("custom_1", 7L, TOKEN, request),
            HttpStatus.BAD_REQUEST
        );
    }

    @Test
    void partialJointPoseIsAcceptedForFlutterRestPoseCompatibility() throws Exception {
        CustomRehabExerciseDto request = validRequest("custom_1");
        request.setKeyframes(new ObjectMapper().readTree("""
            [
              {"id":"a","time":0,"jointRotations":{}},
              {"id":"b","time":1,"jointRotations":{"rightShoulder":{"x":1,"y":2,"z":3}}}
            ]
            """));
        when(exerciseRepository.findById("custom_1")).thenReturn(Optional.empty());
        when(exerciseRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.save("custom_1", 7L, TOKEN, request).getKeyframes())
            .isEqualTo(request.getKeyframes());
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
            .isInstanceOf(CustomExerciseApiException.class)
            .extracting(error -> ((CustomExerciseApiException) error).getStatus())
            .isEqualTo(status);
    }

    private CustomRehabExerciseDto validRequest(String id) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            CustomRehabExerciseDto dto = new CustomRehabExerciseDto();
            dto.setId(id);
            dto.setName("Shoulder");
            dto.setDescription("Slowly");
            dto.setCreatedByTherapistId("7");
            dto.setCreatedAt("2026-09-04T01:02:03Z");
            dto.setUpdatedAt("2026-09-04T01:02:04Z");
            dto.setRepetitions(10);
            dto.setSets(3);
            dto.setHoldSeconds(5.0);
            dto.setRestSeconds(30.0);
            dto.setDuration(1.0);
            dto.setKeyframes(mapper.readTree("""
                [
                  {"id":"kf_001","time":0,"jointRotations":{}},
                  {"id":"kf_002","time":1,"jointRotations":{"rightShoulder":{"x":1,"y":2,"z":3}}}
                ]
                """));
            dto.setEvaluationRules(mapper.readTree("[]"));
            return dto;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private CustomRehabExerciseEntity entity(String id, User owner) {
        CustomRehabExerciseDto request = validRequest(id);
        CustomRehabExerciseEntity entity = new CustomRehabExerciseEntity();
        entity.setId(id);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setCreatedByTherapist(owner);
        entity.setCreatedAt(Instant.parse("2026-09-04T01:02:03Z"));
        entity.setUpdatedAt(Instant.parse("2026-09-04T01:02:04Z"));
        entity.setRepetitions(request.getRepetitions());
        entity.setSets(request.getSets());
        entity.setHoldSeconds(request.getHoldSeconds());
        entity.setRestSeconds(request.getRestSeconds());
        entity.setDuration(request.getDuration());
        entity.setKeyframesJson(request.getKeyframes().toString());
        entity.setEvaluationRulesJson(request.getEvaluationRules().toString());
        return entity;
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
