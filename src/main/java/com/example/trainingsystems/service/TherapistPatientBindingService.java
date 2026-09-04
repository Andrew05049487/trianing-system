package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.TherapistPatientDto;
import com.example.trainingsystems.dto.TherapistPatientLookupDto;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TherapistPatientBindingService {
    private static final String THERAPIST = "THERAPIST";
    private static final String PATIENT = "PATIENT";

    private final UserRepository userRepository;
    private final UserBindingRepository bindingRepository;
    private final ExerciseAssignmentRepository defaultAssignmentRepository;
    private final CustomExerciseAssignmentRepository customAssignmentRepository;
    private final CustomExerciseIdentityService identityService;

    public TherapistPatientBindingService(
        UserRepository userRepository,
        UserBindingRepository bindingRepository,
        ExerciseAssignmentRepository defaultAssignmentRepository,
        CustomExerciseAssignmentRepository customAssignmentRepository,
        CustomExerciseIdentityService identityService
    ) {
        this.userRepository = userRepository;
        this.bindingRepository = bindingRepository;
        this.defaultAssignmentRepository = defaultAssignmentRepository;
        this.customAssignmentRepository = customAssignmentRepository;
        this.identityService = identityService;
    }

    @Transactional(readOnly = true)
    public List<TherapistPatientDto> getPatients(
        Long therapistId,
        String identityToken
    ) {
        requireTherapist(therapistId, identityToken);
        return bindingRepository
            .findAllByLinkedUser_IdAndRelationshipIgnoreCase(
                therapistId,
                THERAPIST
            )
            .stream()
            .filter(binding -> hasRole(binding.getPatient(), PATIENT))
            .sorted(Comparator
                .comparing(
                    (UserBinding binding) -> binding.getPatient().getName(),
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                )
                .thenComparing(binding -> binding.getPatient().getId())
            )
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public TherapistPatientLookupDto lookupPatient(
        String bindingCode,
        Long therapistId,
        String identityToken
    ) {
        requireTherapist(therapistId, identityToken);
        User patient = requirePatientByBindingCode(bindingCode);
        return new TherapistPatientLookupDto(
            patient.getId(),
            patient.getName(),
            patient.getEmail()
        );
    }

    @Transactional
    public TherapistPatientDto bindPatient(
        String bindingCode,
        Long therapistId,
        String identityToken
    ) {
        User therapist = requireTherapist(therapistId, identityToken);
        User patient = requirePatientByBindingCode(bindingCode);
        if (patient.getId().equals(therapist.getId())) {
            throw badRequest("不能綁定自己的帳號");
        }
        if (bindingRepository.existsByPatient_IdAndLinkedUser_Id(
            patient.getId(),
            therapistId
        )) {
            throw new CustomExerciseApiException(
                HttpStatus.CONFLICT,
                "此患者已與目前帳號建立綁定"
            );
        }

        UserBinding binding = new UserBinding();
        binding.setPatient(patient);
        binding.setLinkedUser(therapist);
        binding.setRelationship(THERAPIST);
        return toDto(bindingRepository.save(binding));
    }

    @Transactional
    public void unbindPatient(
        Long patientId,
        Long therapistId,
        String identityToken
    ) {
        requireTherapist(therapistId, identityToken);
        UserBinding binding = bindingRepository
            .findByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patientId,
                therapistId,
                THERAPIST
            )
            .orElseThrow(() -> notFound("找不到此治療師與患者綁定"));

        defaultAssignmentRepository
            .deleteAllByPatient_IdAndAssignedByTherapist_Id(
                patientId,
                therapistId
            );
        customAssignmentRepository
            .deleteAllByPatient_IdAndAssignedByTherapist_Id(
                patientId,
                therapistId
            );
        bindingRepository.delete(binding);
    }

    private User requirePatientByBindingCode(String bindingCode) {
        if (bindingCode == null || bindingCode.isBlank()) {
            throw badRequest("請輸入患者綁定碼");
        }
        User patient = userRepository
            .findByBindingCode(bindingCode.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> notFound("找不到此綁定碼"));
        if (!hasRole(patient, PATIENT)) {
            throw badRequest("此綁定碼不屬於患者帳號");
        }
        return patient;
    }

    private User requireTherapist(Long therapistId, String identityToken) {
        if (therapistId == null) {
            throw badRequest("X-User-Id 不可為空");
        }
        User therapist = userRepository.findById(therapistId).orElseThrow(
            () -> notFound("使用者不存在")
        );
        if (!hasRole(therapist, THERAPIST)) {
            throw forbidden("只有治療師可以管理患者綁定");
        }
        if (!identityService.isConfigured()) {
            throw new CustomExerciseApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Custom Exercise identity 尚未設定"
            );
        }
        if (!identityService.isValid(therapist, identityToken)) {
            throw forbidden("Custom Exercise identity token 無效");
        }
        return therapist;
    }

    private boolean hasRole(User user, String role) {
        return user != null
            && user.getRole() != null
            && role.equals(user.getRole().toUpperCase(Locale.ROOT));
    }

    private TherapistPatientDto toDto(UserBinding binding) {
        User patient = binding.getPatient();
        return new TherapistPatientDto(
            patient.getId(),
            patient.getName(),
            patient.getEmail(),
            binding.getRelationship(),
            binding.getCreatedAt() == null
                ? null
                : binding.getCreatedAt().toString()
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
