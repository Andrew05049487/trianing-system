package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AssignablePatientDto;
import com.example.trainingsystems.dto.CustomExerciseAssignmentDto;
import com.example.trainingsystems.dto.CustomRehabExerciseDto;
import com.example.trainingsystems.entity.CustomExerciseAssignmentEntity;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CustomExerciseAssignmentService {
    private static final String THERAPIST = "THERAPIST";
    private static final String PATIENT = "PATIENT";

    private final CustomExerciseAssignmentRepository assignmentRepository;
    private final CustomRehabExerciseRepository exerciseRepository;
    private final UserBindingRepository bindingRepository;
    private final UserRepository userRepository;
    private final CustomExerciseIdentityService identityService;
    private final CustomRehabExerciseService exerciseService;

    public CustomExerciseAssignmentService(
        CustomExerciseAssignmentRepository assignmentRepository,
        CustomRehabExerciseRepository exerciseRepository,
        UserBindingRepository bindingRepository,
        UserRepository userRepository,
        CustomExerciseIdentityService identityService,
        CustomRehabExerciseService exerciseService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.exerciseRepository = exerciseRepository;
        this.bindingRepository = bindingRepository;
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.exerciseService = exerciseService;
    }

    @Transactional(readOnly = true)
    public List<AssignablePatientDto> getAssignablePatients(
        Long therapistId,
        String identityToken
    ) {
        requireRole(therapistId, identityToken, THERAPIST);
        return bindingRepository
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(therapistId, THERAPIST)
            .stream()
            .map(binding -> binding.getPatient())
            .filter(patient -> hasRole(patient, PATIENT))
            .distinct()
            .sorted(
                Comparator.comparing(
                    User::getName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ).thenComparing(User::getId)
            )
            .map(patient -> new AssignablePatientDto(
                patient.getId(),
                patient.getName()
            ))
            .toList();
    }

    @Transactional
    public CustomExerciseAssignmentDto assign(
        String exerciseId,
        Long patientId,
        Long therapistId,
        String identityToken
    ) {
        User therapist = requireRole(therapistId, identityToken, THERAPIST);
        CustomRehabExerciseEntity exercise = requireOwnedExercise(
            exerciseId,
            therapistId
        );
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

        CustomExerciseAssignmentEntity assignment = assignmentRepository
            .findByCustomExercise_IdAndPatient_Id(exerciseId, patientId)
            .orElseGet(CustomExerciseAssignmentEntity::new);
        assignment.setCustomExercise(exercise);
        assignment.setPatient(patient);
        assignment.setAssignedByTherapist(therapist);
        assignment.setActive(true);

        return toDto(assignmentRepository.save(assignment));
    }

    @Transactional
    public void unassign(
        String exerciseId,
        Long patientId,
        Long therapistId,
        String identityToken
    ) {
        requireRole(therapistId, identityToken, THERAPIST);
        requireOwnedExercise(exerciseId, therapistId);
        assignmentRepository
            .findByCustomExercise_IdAndPatient_Id(exerciseId, patientId)
            .ifPresent(assignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<CustomExerciseAssignmentDto> getExerciseAssignments(
        String exerciseId,
        Long therapistId,
        String identityToken
    ) {
        requireRole(therapistId, identityToken, THERAPIST);
        requireOwnedExercise(exerciseId, therapistId);
        Sort sort = Sort.by(
            Sort.Order.asc("patient.name"),
            Sort.Order.asc("patient.id")
        );
        return assignmentRepository
            .findAllByCustomExercise_IdAndAssignedByTherapist_IdAndActiveTrue(
                exerciseId,
                therapistId,
                sort
            )
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomRehabExerciseDto> getPatientExercises(
        Long patientId,
        String identityToken
    ) {
        requireRole(patientId, identityToken, PATIENT);
        Sort sort = Sort.by(
            Sort.Order.desc("customExercise.updatedAt"),
            Sort.Order.asc("customExercise.id")
        );
        return assignmentRepository
            .findAllByPatient_IdAndActiveTrue(patientId, sort)
            .stream()
            .map(CustomExerciseAssignmentEntity::getCustomExercise)
            .map(exerciseService::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public CustomRehabExerciseDto getPatientExercise(
        String exerciseId,
        Long patientId,
        String identityToken
    ) {
        requireRole(patientId, identityToken, PATIENT);
        return assignmentRepository
            .findByCustomExercise_IdAndPatient_IdAndActiveTrue(
                exerciseId,
                patientId
            )
            .map(CustomExerciseAssignmentEntity::getCustomExercise)
            .map(exerciseService::toDto)
            .orElseThrow(() -> notFound("找不到已指派的自訂動作"));
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

    private CustomRehabExerciseEntity requireOwnedExercise(
        String exerciseId,
        Long therapistId
    ) {
        return exerciseRepository
            .findByIdAndCreatedByTherapist_Id(exerciseId, therapistId)
            .orElseThrow(() -> {
                if (exerciseRepository.existsById(exerciseId)) {
                    return forbidden("無權指派其他治療師的自訂動作");
                }
                return notFound("找不到自訂動作");
            });
    }

    private CustomExerciseAssignmentDto toDto(
        CustomExerciseAssignmentEntity assignment
    ) {
        CustomExerciseAssignmentDto dto = new CustomExerciseAssignmentDto();
        dto.setAssignmentId(assignment.getId());
        dto.setExerciseId(assignment.getCustomExercise().getId());
        dto.setExerciseName(assignment.getCustomExercise().getName());
        dto.setExerciseDescription(assignment.getCustomExercise().getDescription());
        dto.setTherapistId(assignment.getAssignedByTherapist().getId());
        dto.setTherapistName(assignment.getAssignedByTherapist().getName());
        dto.setPatientId(assignment.getPatient().getId());
        dto.setPatientName(assignment.getPatient().getName());
        dto.setAssignedAt(assignment.getAssignedAt().toString());
        return dto;
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
