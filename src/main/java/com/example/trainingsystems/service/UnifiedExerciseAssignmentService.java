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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UnifiedExerciseAssignmentService {
    private static final String DEFAULT = "DEFAULT";
    private static final String CUSTOM = "CUSTOM";
    private static final String THERAPIST = "THERAPIST";
    private static final String PATIENT = "PATIENT";

    private final ExerciseAssignmentRepository defaultAssignmentRepository;
    private final ExerciseRepository defaultExerciseRepository;
    private final CustomExerciseAssignmentRepository customAssignmentRepository;
    private final CustomRehabExerciseRepository customExerciseRepository;
    private final UserBindingRepository bindingRepository;
    private final UserRepository userRepository;
    private final CustomExerciseIdentityService identityService;
    private final CustomExerciseAssignmentService customAssignmentService;

    public UnifiedExerciseAssignmentService(
        ExerciseAssignmentRepository defaultAssignmentRepository,
        ExerciseRepository defaultExerciseRepository,
        CustomExerciseAssignmentRepository customAssignmentRepository,
        CustomRehabExerciseRepository customExerciseRepository,
        UserBindingRepository bindingRepository,
        UserRepository userRepository,
        CustomExerciseIdentityService identityService,
        CustomExerciseAssignmentService customAssignmentService
    ) {
        this.defaultAssignmentRepository = defaultAssignmentRepository;
        this.defaultExerciseRepository = defaultExerciseRepository;
        this.customAssignmentRepository = customAssignmentRepository;
        this.customExerciseRepository = customExerciseRepository;
        this.bindingRepository = bindingRepository;
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.customAssignmentService = customAssignmentService;
    }

    @Transactional(readOnly = true)
    public List<AssignableExerciseDto> getAssignableExercises(
        Long therapistId,
        String identityToken,
        Long patientId
    ) {
        requireRole(therapistId, identityToken, THERAPIST);
        requireBoundPatient(patientId, therapistId);

        Set<Long> assignedDefaultIds = new HashSet<>();
        defaultAssignmentRepository
            .findAllByPatient_IdAndAssignedByTherapist_IdAndActiveTrue(
                patientId,
                therapistId
            )
            .forEach(item -> assignedDefaultIds.add(item.getExercise().getId()));

        Set<String> assignedCustomIds = new HashSet<>();
        customAssignmentRepository
            .findAllByPatient_IdAndAssignedByTherapist_IdAndActiveTrue(
                patientId,
                therapistId
            )
            .forEach(item -> assignedCustomIds.add(item.getCustomExercise().getId()));

        List<AssignableExerciseDto> result = new ArrayList<>();
        defaultExerciseRepository
            .findAll(Sort.by(Sort.Order.asc("exerciseName"), Sort.Order.asc("id")))
            .forEach(exercise -> result.add(toDefaultDto(
                exercise,
                assignedDefaultIds.contains(exercise.getId())
            )));
        customExerciseRepository
            .findAllByCreatedByTherapist_Id(
                therapistId,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
            )
            .forEach(exercise -> result.add(toCustomDto(
                exercise,
                assignedCustomIds.contains(exercise.getId())
            )));
        return result;
    }

    @Transactional
    public AssignableExerciseDto assign(
        String type,
        String exerciseId,
        Long patientId,
        Long therapistId,
        String identityToken
    ) {
        String normalizedType = normalizeType(type);
        if (CUSTOM.equals(normalizedType)) {
            CustomExerciseAssignmentDto assignment = customAssignmentService.assign(
                exerciseId,
                patientId,
                therapistId,
                identityToken
            );
            return new AssignableExerciseDto(
                assignment.getExerciseId(),
                assignment.getExerciseName(),
                assignment.getExerciseDescription(),
                CUSTOM,
                true
            );
        }

        User therapist = requireRole(therapistId, identityToken, THERAPIST);
        User patient = requireBoundPatient(patientId, therapistId);
        Exercise exercise = defaultExerciseRepository
            .findById(parseDefaultId(exerciseId))
            .orElseThrow(() -> notFound("找不到預設復健動作"));

        ExerciseAssignmentEntity assignment = defaultAssignmentRepository
            .findByExercise_IdAndPatient_Id(exercise.getId(), patientId)
            .orElseGet(ExerciseAssignmentEntity::new);
        assignment.setExercise(exercise);
        assignment.setPatient(patient);
        assignment.setAssignedByTherapist(therapist);
        assignment.setActive(true);
        defaultAssignmentRepository.save(assignment);
        return toDefaultDto(exercise, true);
    }

    @Transactional
    public void unassign(
        String type,
        String exerciseId,
        Long patientId,
        Long therapistId,
        String identityToken
    ) {
        String normalizedType = normalizeType(type);
        if (CUSTOM.equals(normalizedType)) {
            customAssignmentService.unassign(
                exerciseId,
                patientId,
                therapistId,
                identityToken
            );
            return;
        }

        requireRole(therapistId, identityToken, THERAPIST);
        requireBoundPatient(patientId, therapistId);
        Long defaultId = parseDefaultId(exerciseId);
        if (!defaultExerciseRepository.existsById(defaultId)) {
            throw notFound("找不到預設復健動作");
        }
        defaultAssignmentRepository
            .findByExercise_IdAndPatient_Id(defaultId, patientId)
            .filter(item -> item.getAssignedByTherapist().getId().equals(therapistId))
            .ifPresent(defaultAssignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<AssignableExerciseDto> getPatientAssignedExercises(
        Long patientId,
        String identityToken
    ) {
        requireRole(patientId, identityToken, PATIENT);
        List<AssignableExerciseDto> result = new ArrayList<>();
        defaultAssignmentRepository
            .findAllByPatient_IdAndActiveTrue(
                patientId,
                Sort.by(
                    Sort.Order.asc("exercise.exerciseName"),
                    Sort.Order.asc("exercise.id")
                )
            )
            .forEach(item -> result.add(toDefaultDto(item.getExercise(), true)));
        customAssignmentRepository
            .findAllByPatient_IdAndActiveTrue(
                patientId,
                Sort.by(
                    Sort.Order.asc("customExercise.name"),
                    Sort.Order.asc("customExercise.id")
                )
            )
            .forEach(item -> result.add(toCustomDto(item.getCustomExercise(), true)));
        return result;
    }

    private User requireBoundPatient(Long patientId, Long therapistId) {
        User patient = userRepository.findById(patientId).orElseThrow(
            () -> notFound("找不到患者")
        );
        if (!hasRole(patient, PATIENT)) {
            throw badRequest("指派目標必須是患者帳號");
        }
        if (!bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patientId,
                therapistId,
                THERAPIST
            )) {
            throw forbidden("只能指派給已綁定的患者");
        }
        return patient;
    }

    private User requireRole(
        Long userId,
        String identityToken,
        String requiredRole
    ) {
        if (userId == null) {
            throw badRequest("X-User-Id 不可為空");
        }
        User user = userRepository.findById(userId).orElseThrow(
            () -> notFound("使用者不存在")
        );
        if (!hasRole(user, requiredRole)) {
            throw forbidden(THERAPIST.equals(requiredRole)
                ? "只有治療師可以管理動作指派"
                : "只有患者可以讀取已指派動作");
        }
        if (!identityService.isConfigured()) {
            throw new CustomExerciseApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Custom Exercise identity 尚未設定"
            );
        }
        if (!identityService.isValid(user, identityToken)) {
            throw forbidden("Custom Exercise identity token 無效");
        }
        return user;
    }

    private boolean hasRole(User user, String role) {
        return user != null
            && user.getRole() != null
            && role.equals(user.getRole().toUpperCase(Locale.ROOT));
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!DEFAULT.equals(normalized) && !CUSTOM.equals(normalized)) {
            throw badRequest("不支援的復健動作類型");
        }
        return normalized;
    }

    private Long parseDefaultId(String exerciseId) {
        try {
            return Long.valueOf(exerciseId);
        } catch (NumberFormatException error) {
            throw badRequest("預設復健動作 ID 格式錯誤");
        }
    }

    private AssignableExerciseDto toDefaultDto(Exercise exercise, boolean assigned) {
        return new AssignableExerciseDto(
            exercise.getId().toString(),
            exercise.getExerciseName(),
            exercise.getDescription(),
            DEFAULT,
            assigned
        );
    }

    private AssignableExerciseDto toCustomDto(
        CustomRehabExerciseEntity exercise,
        boolean assigned
    ) {
        return new AssignableExerciseDto(
            exercise.getId(),
            exercise.getName(),
            exercise.getDescription(),
            CUSTOM,
            assigned
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
