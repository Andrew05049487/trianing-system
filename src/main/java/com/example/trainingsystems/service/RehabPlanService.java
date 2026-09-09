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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RehabPlanService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final String PATIENT = "PATIENT";
    private static final String THERAPIST = "THERAPIST";

    private final RehabPlanRepository planRepository;
    private final UserRepository userRepository;
    private final UserBindingRepository bindingRepository;
    private final CustomExerciseIdentityService identityService;
    private final Clock clock;

    public RehabPlanService(
        RehabPlanRepository planRepository,
        UserRepository userRepository,
        UserBindingRepository bindingRepository,
        CustomExerciseIdentityService identityService,
        Clock clock
    ) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.bindingRepository = bindingRepository;
        this.identityService = identityService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RehabPlanResponse getPlan(
        String patientIdValue,
        LocalDate date,
        Long requesterId,
        String identityToken
    ) {
        Long patientId = parsePatientId(patientIdValue);
        authorizeRead(patientId, requesterId, identityToken);
        RehabPlanEntity plan = planRepository
            .findByPatient_IdAndPlanDate(patientId, requireDate(date))
            .orElseThrow(() -> notFound("Rehabilitation plan not found"));
        return toResponse(plan);
    }

    @Transactional
    public RehabPlanResponse savePlan(
        RehabPlanRequest request,
        Long requesterId,
        String identityToken
    ) {
        ValidatedPlan validated = validate(request);
        User therapist = authenticate(requesterId, identityToken);
        requireRole(therapist, THERAPIST, "Only therapists can save rehabilitation plans");
        rejectPastWrite(validated.date());

        User patient = userRepository.findByIdForUpdate(validated.patientId())
            .orElseThrow(() -> notFound("Patient not found"));
        requireRole(patient, PATIENT, "Rehabilitation plan target must be a patient");
        requireBinding(patient.getId(), therapist.getId());

        RehabPlanEntity plan = planRepository
            .findByPatient_IdAndPlanDate(patient.getId(), validated.date())
            .orElse(null);

        RehabPlanEntity planWithSamePublicId = planRepository
            .findByPlanId(validated.planId())
            .orElse(null);
        if (planWithSamePublicId != null && planWithSamePublicId != plan &&
            (plan == null || !planWithSamePublicId.getId().equals(plan.getId()))) {
            throw conflict("planId is already used by another rehabilitation plan");
        }

        if (plan == null) {
            plan = new RehabPlanEntity();
            plan.setPatient(patient);
            plan.setPlanId(validated.planId());
            plan.setCreatedBy(validated.createdBy());
            plan.setPlanDate(validated.date());
        } else if (!plan.getPlanId().equals(validated.planId())) {
            throw conflict("An existing plan for this patient and date has a different planId");
        }
        plan.setCondition(validated.condition());
        mergeItems(plan, validated.items());
        return toResponse(planRepository.save(plan));
    }

    @Transactional
    public RehabPlanResponse updatePlanItem(
        String patientIdValue,
        String planId,
        String exerciseId,
        RehabPlanItemDto request,
        Long requesterId,
        String identityToken
    ) {
        Long patientId = parsePatientId(patientIdValue);
        User requester = authorizeRead(patientId, requesterId, identityToken);
        String normalizedPlanId = requireText(planId, "planId", 100);
        RehabPlanItemDto validated = validateItem(request, exerciseId);

        RehabPlanEntity plan = planRepository
            .findForUpdate(patientId, normalizedPlanId)
            .orElseThrow(() -> notFound("Rehabilitation plan not found"));
        rejectPastWrite(plan.getPlanDate());

        RehabPlanItemEntity item = plan.getItems().stream()
            .filter(candidate -> candidate.getExerciseId().equals(exerciseId))
            .findFirst()
            .orElseThrow(() -> notFound("Rehabilitation plan item not found"));

        if (hasRole(requester, PATIENT)) {
            if (!plan.getPlanDate().equals(currentBusinessDate())) {
                throw forbidden("Patients can only train on today's rehabilitation plan");
            }
            if (!item.getItemOrder().equals(validated.order()) ||
                !item.getSets().equals(validated.sets()) ||
                !item.getRepsPerSet().equals(validated.repsPerSet()) ||
                !Boolean.TRUE.equals(validated.done())) {
                throw forbidden("Patients may only mark today's plan item as done");
            }
            item.setDone(true);
        } else {
            item.setItemOrder(validated.order());
            item.setSets(validated.sets());
            item.setRepsPerSet(validated.repsPerSet());
            item.setDone(item.isDone() || Boolean.TRUE.equals(validated.done()));
        }
        return toResponse(planRepository.save(plan));
    }

    LocalDate currentBusinessDate() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    private User authorizeRead(
        Long patientId,
        Long requesterId,
        String identityToken
    ) {
        User requester = authenticate(requesterId, identityToken);
        User patient = userRepository.findById(patientId)
            .orElseThrow(() -> notFound("Patient not found"));
        requireRole(patient, PATIENT, "Rehabilitation plan target must be a patient");
        if (hasRole(requester, PATIENT)) {
            if (!requester.getId().equals(patientId)) {
                throw forbidden("Patients can only access their own rehabilitation plans");
            }
            return requester;
        }
        requireRole(requester, THERAPIST, "Only the patient or a therapist can access this plan");
        requireBinding(patientId, requester.getId());
        return requester;
    }

    private User authenticate(Long requesterId, String identityToken) {
        if (requesterId == null) throw badRequest("X-User-Id is required");
        User requester = userRepository.findById(requesterId)
            .orElseThrow(() -> notFound("User not found"));
        if (!identityService.isConfigured()) {
            throw new RehabPlanApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Identity verification is not configured"
            );
        }
        if (!identityService.isValid(requester, identityToken)) {
            throw forbidden("Identity token is invalid");
        }
        return requester;
    }

    private void requireBinding(Long patientId, Long therapistId) {
        if (!bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                patientId,
                therapistId,
                THERAPIST
            )) {
            throw forbidden("Therapist is not bound to this patient");
        }
    }

    private ValidatedPlan validate(RehabPlanRequest request) {
        if (request == null) throw badRequest("Request body is required");
        Long patientId = parsePatientId(request.patientId());
        String planId = requireText(request.planId(), "planId", 100);
        String createdBy = requireText(request.createdBy(), "createdBy", 50);
        LocalDate date = parsePlanDate(request.date());
        String condition = requireText(request.condition(), "condition", 20)
            .toLowerCase(Locale.ROOT);
        if (!condition.equals("fracture") && !condition.equals("stroke")) {
            throw badRequest("condition must be fracture or stroke");
        }
        if (request.items() == null) throw badRequest("items is required");
        List<RehabPlanItemDto> items = new ArrayList<>();
        Set<String> exerciseIds = new HashSet<>();
        for (RehabPlanItemDto item : request.items()) {
            RehabPlanItemDto validItem = validateItem(item, null);
            if (!exerciseIds.add(validItem.exerciseId())) {
                throw badRequest("Duplicate exerciseId is not allowed");
            }
            items.add(validItem);
        }
        return new ValidatedPlan(
            patientId,
            planId,
            createdBy,
            date,
            condition,
            items
        );
    }

    private RehabPlanItemDto validateItem(
        RehabPlanItemDto item,
        String pathExerciseId
    ) {
        if (item == null) throw badRequest("Plan item is required");
        String exerciseId = requireText(item.exerciseId(), "exerciseId", 100);
        if (pathExerciseId != null && !exerciseId.equals(pathExerciseId)) {
            throw badRequest("Path exerciseId must match body exerciseId");
        }
        if (item.order() == null || item.order() < 0) {
            throw badRequest("order must be greater than or equal to 0");
        }
        if (item.sets() == null || item.sets() <= 0) {
            throw badRequest("sets must be greater than 0");
        }
        if (item.repsPerSet() == null || item.repsPerSet() <= 0) {
            throw badRequest("repsPerSet must be greater than 0");
        }
        return new RehabPlanItemDto(
            exerciseId,
            item.order(),
            item.sets(),
            item.repsPerSet(),
            Boolean.TRUE.equals(item.done())
        );
    }

    private void mergeItems(
        RehabPlanEntity plan,
        List<RehabPlanItemDto> requestedItems
    ) {
        Map<String, RehabPlanItemEntity> existingByExercise = new HashMap<>();
        for (RehabPlanItemEntity item : plan.getItems()) {
            existingByExercise.put(item.getExerciseId(), item);
        }

        for (RehabPlanItemDto requested : requestedItems) {
            RehabPlanItemEntity item = existingByExercise.remove(requested.exerciseId());
            if (item == null) {
                item = new RehabPlanItemEntity();
                item.setExerciseId(requested.exerciseId());
                item.setDone(false);
                plan.addItem(item);
            }
            item.setItemOrder(requested.order());
            item.setSets(requested.sets());
            item.setRepsPerSet(requested.repsPerSet());
        }
        for (RehabPlanItemEntity removed : existingByExercise.values()) {
            plan.removeItem(removed);
        }
        plan.getItems().sort((left, right) ->
            left.getItemOrder().compareTo(right.getItemOrder()));
    }

    private RehabPlanResponse toResponse(RehabPlanEntity plan) {
        List<RehabPlanItemDto> items = plan.getItems().stream()
            .sorted((left, right) ->
                left.getItemOrder().compareTo(right.getItemOrder()))
            .map(item -> new RehabPlanItemDto(
                item.getExerciseId(),
                item.getItemOrder(),
                item.getSets(),
                item.getRepsPerSet(),
                item.isDone()
            ))
            .toList();
        return new RehabPlanResponse(
            plan.getPatient().getId().toString(),
            plan.getPlanId(),
            plan.getCreatedBy(),
            plan.getPlanDate().toString(),
            plan.getCondition(),
            items
        );
    }

    private LocalDate parsePlanDate(String value) {
        String date = requireText(value, "date", 64);
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(date).toLocalDate();
            } catch (DateTimeParseException ignoredDateTime) {
                try {
                    return OffsetDateTime.parse(date).toLocalDate();
                } catch (DateTimeParseException error) {
                    throw badRequest("date must be an ISO date or date-time");
                }
            }
        }
    }

    private Long parsePatientId(String value) {
        String patientId = requireText(value, "patientId", 32);
        try {
            long parsed = Long.parseLong(patientId);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw badRequest("patientId must be a positive numeric user ID");
        }
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) throw badRequest("date is required");
        return date;
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw badRequest(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw badRequest(field + " is too long");
        }
        return normalized;
    }

    private void rejectPastWrite(LocalDate planDate) {
        if (planDate.isBefore(currentBusinessDate())) {
            throw conflict("Past rehabilitation plans are read-only");
        }
    }

    private void requireRole(User user, String role, String message) {
        if (!hasRole(user, role)) throw forbidden(message);
    }

    private boolean hasRole(User user, String role) {
        return user != null && user.getRole() != null &&
            role.equals(user.getRole().toUpperCase(Locale.ROOT));
    }

    private RehabPlanApiException badRequest(String message) {
        return new RehabPlanApiException(HttpStatus.BAD_REQUEST, message);
    }

    private RehabPlanApiException forbidden(String message) {
        return new RehabPlanApiException(HttpStatus.FORBIDDEN, message);
    }

    private RehabPlanApiException notFound(String message) {
        return new RehabPlanApiException(HttpStatus.NOT_FOUND, message);
    }

    private RehabPlanApiException conflict(String message) {
        return new RehabPlanApiException(HttpStatus.CONFLICT, message);
    }

    private record ValidatedPlan(
        Long patientId,
        String planId,
        String createdBy,
        LocalDate date,
        String condition,
        List<RehabPlanItemDto> items
    ) {}
}
