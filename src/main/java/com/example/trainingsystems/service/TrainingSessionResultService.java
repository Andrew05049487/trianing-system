package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.TrainingSessionResultDto;
import com.example.trainingsystems.dto.TrainingSessionResultRequest;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.Exercise;
import com.example.trainingsystems.entity.TrainingSessionResultEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.ExerciseRepository;
import com.example.trainingsystems.repository.TrainingSessionResultRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TrainingSessionResultService {
    private static final String PATIENT = "PATIENT";
    private static final String THERAPIST = "THERAPIST";
    private static final String DEFAULT = "DEFAULT";
    private static final String CUSTOM = "CUSTOM";
    private static final String COMPLETED = "COMPLETED";

    private final TrainingSessionResultRepository resultRepository;
    private final UserRepository userRepository;
    private final ExerciseRepository defaultExerciseRepository;
    private final CustomRehabExerciseRepository customExerciseRepository;
    private final ExerciseAssignmentRepository defaultAssignmentRepository;
    private final CustomExerciseAssignmentRepository customAssignmentRepository;
    private final UserBindingRepository bindingRepository;
    private final CustomExerciseIdentityService identityService;

    public TrainingSessionResultService(
        TrainingSessionResultRepository resultRepository,
        UserRepository userRepository,
        ExerciseRepository defaultExerciseRepository,
        CustomRehabExerciseRepository customExerciseRepository,
        ExerciseAssignmentRepository defaultAssignmentRepository,
        CustomExerciseAssignmentRepository customAssignmentRepository,
        UserBindingRepository bindingRepository,
        CustomExerciseIdentityService identityService
    ) {
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.defaultExerciseRepository = defaultExerciseRepository;
        this.customExerciseRepository = customExerciseRepository;
        this.defaultAssignmentRepository = defaultAssignmentRepository;
        this.customAssignmentRepository = customAssignmentRepository;
        this.bindingRepository = bindingRepository;
        this.identityService = identityService;
    }

    @Transactional
    public TrainingSessionResultDto save(
        Long patientId,
        String identityToken,
        TrainingSessionResultRequest request
    ) {
        User patient = requireRole(patientId, identityToken, PATIENT);
        validate(request);

        TrainingSessionResultEntity existing = resultRepository
            .findBySessionId(request.getSessionId())
            .orElse(null);
        if (existing != null) return requireSamePatient(existing, patientId);

        String type = request.getExerciseType().trim().toUpperCase(Locale.ROOT);
        String exerciseName = requireAssignedExerciseName(
            type,
            request.getExerciseId(),
            patientId
        );
        Instant completedAt = Instant.now();
        long duration = request.getDurationSeconds();

        TrainingSessionResultEntity entity = new TrainingSessionResultEntity();
        entity.setSessionId(request.getSessionId());
        entity.setPatient(patient);
        entity.setExerciseType(type);
        entity.setExerciseId(request.getExerciseId());
        entity.setExerciseName(exerciseName);
        entity.setCompletedSets(request.getCompletedSets());
        entity.setCompletedReps(request.getCompletedReps());
        entity.setTargetSets(request.getTargetSets());
        entity.setTargetReps(request.getTargetReps());
        entity.setDurationSeconds(duration);
        entity.setStartedAt(completedAt.minusSeconds(duration));
        entity.setCompletedAt(completedAt);
        entity.setCompletionStatus(COMPLETED);
        entity.setScore(score(request));
        try {
            return toDto(resultRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException conflict) {
            throw new CustomExerciseApiException(
                HttpStatus.CONFLICT,
                "相同 session 的訓練結果已建立，請重新讀取紀錄"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResultDto> getMine(
        Long patientId,
        String identityToken
    ) {
        requireRole(patientId, identityToken, PATIENT);
        return resultRepository.findAllByPatient_IdOrderByCompletedAtDesc(patientId)
            .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<TrainingSessionResultDto> getForTherapist(
        Long therapistId,
        String identityToken,
        Long patientId
    ) {
        requireRole(therapistId, identityToken, THERAPIST);
        if (!bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patientId,
                therapistId,
                THERAPIST
            )) {
            throw forbidden("只能查看已綁定患者的訓練紀錄");
        }
        return resultRepository.findAllByPatient_IdOrderByCompletedAtDesc(patientId)
            .stream().map(this::toDto).toList();
    }

    private TrainingSessionResultDto requireSamePatient(
        TrainingSessionResultEntity entity,
        Long patientId
    ) {
        if (!entity.getPatient().getId().equals(patientId)) {
            throw forbidden("訓練 session 已由其他使用者使用");
        }
        return toDto(entity);
    }

    private String requireAssignedExerciseName(
        String type,
        String exerciseId,
        Long patientId
    ) {
        if (CUSTOM.equals(type)) {
            customAssignmentRepository
                .findByCustomExercise_IdAndPatient_IdAndActiveTrue(
                    exerciseId,
                    patientId
                )
                .orElseThrow(() -> forbidden("此自訂動作未指派給目前患者"));
            CustomRehabExerciseEntity exercise = customExerciseRepository
                .findById(exerciseId)
                .orElseThrow(() -> notFound("找不到自訂復健動作"));
            return exercise.getName();
        }

        Long defaultId;
        try {
            defaultId = Long.valueOf(exerciseId);
        } catch (NumberFormatException error) {
            throw badRequest("預設動作 ID 格式錯誤");
        }
        boolean assigned = defaultAssignmentRepository
            .findByExercise_IdAndPatient_Id(defaultId, patientId)
            .filter(item -> item.isActive())
            .isPresent();
        if (!assigned) throw forbidden("此預設動作未指派給目前患者");
        Exercise exercise = defaultExerciseRepository.findById(defaultId)
            .orElseThrow(() -> notFound("找不到預設復健動作"));
        return exercise.getExerciseName();
    }

    private void validate(TrainingSessionResultRequest request) {
        if (request == null) throw badRequest("訓練結果不可為空");
        try {
            UUID.fromString(request.getSessionId());
        } catch (Exception error) {
            throw badRequest("sessionId 格式錯誤");
        }
        String type = request.getExerciseType() == null
            ? ""
            : request.getExerciseType().trim().toUpperCase(Locale.ROOT);
        if (!DEFAULT.equals(type) && !CUSTOM.equals(type)) {
            throw badRequest("不支援的動作類型");
        }
        if (request.getExerciseId() == null || request.getExerciseId().isBlank()) {
            throw badRequest("exerciseId 不可為空");
        }
        if (!COMPLETED.equals(request.getCompletionStatus())) {
            throw badRequest("目前只接受已完成的訓練結果");
        }
        positive(request.getTargetSets(), "targetSets");
        positive(request.getTargetReps(), "targetReps");
        positive(request.getCompletedSets(), "completedSets");
        positive(request.getCompletedReps(), "completedReps");
        long total = (long) request.getTargetSets() * request.getTargetReps();
        if (request.getCompletedSets() > request.getTargetSets()
            || request.getCompletedReps() > total) {
            throw badRequest("完成組次不可超過目標組次");
        }
        if (!request.getCompletedSets().equals(request.getTargetSets())
            || request.getCompletedReps() != total) {
            throw badRequest("COMPLETED 結果必須完成全部目標組次");
        }
        if (request.getDurationSeconds() == null
            || request.getDurationSeconds() < 0
            || request.getDurationSeconds() > 86400) {
            throw badRequest("durationSeconds 格式錯誤");
        }
    }

    private void positive(Integer value, String field) {
        if (value == null || value <= 0 || value > 1000) {
            throw badRequest(field + " 必須介於 1 到 1000");
        }
    }

    private BigDecimal score(TrainingSessionResultRequest request) {
        long target = (long) request.getTargetSets() * request.getTargetReps();
        return BigDecimal.valueOf(request.getCompletedReps())
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(target), 2, RoundingMode.HALF_UP)
            .max(BigDecimal.ZERO)
            .min(BigDecimal.valueOf(100));
    }

    private User requireRole(Long userId, String token, String role) {
        if (userId == null) throw badRequest("X-User-Id 不可為空");
        User user = userRepository.findById(userId)
            .orElseThrow(() -> notFound("使用者不存在"));
        if (user.getRole() == null || !role.equalsIgnoreCase(user.getRole())) {
            throw forbidden("帳號角色無權執行此操作");
        }
        if (!identityService.isConfigured()) {
            throw new CustomExerciseApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "訓練結果 identity 尚未設定"
            );
        }
        if (!identityService.isValid(user, token)) {
            throw forbidden("Identity token 無效");
        }
        return user;
    }

    public TrainingSessionResultDto toDto(TrainingSessionResultEntity entity) {
        return new TrainingSessionResultDto(
            entity.getSessionId(),
            entity.getExerciseType(),
            entity.getExerciseId(),
            entity.getExerciseName(),
            entity.getCompletedSets(),
            entity.getCompletedReps(),
            entity.getTargetSets(),
            entity.getTargetReps(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getDurationSeconds(),
            entity.getCompletionStatus(),
            entity.getScore()
        );
    }

    private CustomExerciseApiException badRequest(String message) {
        return new CustomExerciseApiException(HttpStatus.BAD_REQUEST, message);
    }

    private CustomExerciseApiException forbidden(String message) {
        return new CustomExerciseApiException(HttpStatus.FORBIDDEN, message);
    }

    private CustomExerciseApiException notFound(String message) {
        return new CustomExerciseApiException(HttpStatus.NOT_FOUND, message);
    }
}
