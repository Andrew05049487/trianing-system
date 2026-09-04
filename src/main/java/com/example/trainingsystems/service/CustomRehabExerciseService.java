package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.CustomRehabExerciseDto;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class CustomRehabExerciseService {
    private static final Set<String> JOINT_NAMES = Set.of(
        "leftShoulder",
        "rightShoulder",
        "leftElbow",
        "rightElbow",
        "leftWrist",
        "rightWrist",
        "leftHip",
        "rightHip",
        "leftKnee",
        "rightKnee",
        "leftAnkle",
        "rightAnkle"
    );

    private final CustomRehabExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CustomExerciseIdentityService identityService;

    public CustomRehabExerciseService(
        CustomRehabExerciseRepository exerciseRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        CustomExerciseIdentityService identityService
    ) {
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.identityService = identityService;
    }

    @Transactional
    public CustomRehabExerciseDto save(
        String pathId,
        Long therapistId,
        String identityToken,
        CustomRehabExerciseDto request
    ) {
        User therapist = requireTherapist(therapistId, identityToken);
        validate(pathId, request);

        Optional<CustomRehabExerciseEntity> existing = exerciseRepository.findById(pathId);
        if (existing.isPresent()
            && !existing.get().getCreatedByTherapist().getId().equals(therapistId)) {
            throw forbidden("無權修改其他治療師的自訂動作");
        }

        Instant now = Instant.now();
        CustomRehabExerciseEntity entity = existing.orElseGet(() -> {
            CustomRehabExerciseEntity created = new CustomRehabExerciseEntity();
            created.setId(pathId);
            created.setCreatedByTherapist(therapist);
            created.setCreatedAt(now);
            return created;
        });

        entity.setName(request.getName().trim());
        entity.setDescription(
            request.getDescription() == null ? "" : request.getDescription().trim()
        );
        entity.setUpdatedAt(now);
        entity.setRepetitions(request.getRepetitions());
        entity.setSets(request.getSets());
        entity.setHoldSeconds(request.getHoldSeconds());
        entity.setRestSeconds(request.getRestSeconds());
        entity.setDuration(request.getDuration());
        entity.setKeyframesJson(writeJson(request.getKeyframes()));
        entity.setEvaluationRulesJson(writeJson(request.getEvaluationRules()));

        return toDto(exerciseRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CustomRehabExerciseDto get(
        String id,
        Long therapistId,
        String identityToken
    ) {
        requireTherapist(therapistId, identityToken);
        return toDto(requireOwnedExercise(id, therapistId));
    }

    @Transactional(readOnly = true)
    public List<CustomRehabExerciseDto> getAll(
        Long therapistId,
        String identityToken
    ) {
        requireTherapist(therapistId, identityToken);
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));
        return exerciseRepository
            .findAllByCreatedByTherapist_Id(therapistId, sort)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public void delete(String id, Long therapistId, String identityToken) {
        requireTherapist(therapistId, identityToken);
        exerciseRepository.delete(requireOwnedExercise(id, therapistId));
    }

    private User requireTherapist(Long userId, String identityToken) {
        if (userId == null) {
            throw badRequest("X-User-Id 不可為空");
        }
        User user = userRepository.findById(userId).orElseThrow(
            () -> new CustomExerciseApiException(HttpStatus.NOT_FOUND, "使用者不存在")
        );
        String role = user.getRole();
        if (role == null || !"THERAPIST".equals(role.toUpperCase(Locale.ROOT))) {
            throw forbidden("只有治療師可以管理自訂動作");
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

    private CustomRehabExerciseEntity requireOwnedExercise(String id, Long therapistId) {
        return exerciseRepository
            .findByIdAndCreatedByTherapist_Id(id, therapistId)
            .orElseThrow(() -> {
                if (exerciseRepository.existsById(id)) {
                    return forbidden("無權存取其他治療師的自訂動作");
                }
                return new CustomExerciseApiException(
                    HttpStatus.NOT_FOUND,
                    "找不到自訂動作"
                );
            });
    }

    private void validate(String pathId, CustomRehabExerciseDto request) {
        if (request == null) {
            throw badRequest("Request body 不可為空");
        }
        if (pathId == null || pathId.isBlank() || pathId.length() > 128) {
            throw badRequest("id 不可為空且長度不可超過 128");
        }
        if (request.getId() == null || !pathId.equals(request.getId())) {
            throw badRequest("Path id 必須與 body id 相同");
        }
        if (request.getName() == null
            || request.getName().isBlank()
            || request.getName().trim().length() > 200) {
            throw badRequest("name 不可空白且長度不可超過 200");
        }
        if (request.getDescription() != null
            && request.getDescription().trim().length() > 2000) {
            throw badRequest("description 長度不可超過 2000");
        }
        if (request.getRepetitions() == null || request.getRepetitions() <= 0) {
            throw badRequest("repetitions 必須大於 0");
        }
        if (request.getSets() == null || request.getSets() <= 0) {
            throw badRequest("sets 必須大於 0");
        }
        requireNonNegativeFinite(request.getHoldSeconds(), "holdSeconds");
        requireNonNegativeFinite(request.getRestSeconds(), "restSeconds");
        requireNonNegativeFinite(request.getDuration(), "duration");
        validateKeyframes(request.getKeyframes(), request.getDuration());
        if (request.getEvaluationRules() == null
            || !request.getEvaluationRules().isArray()) {
            throw badRequest("evaluationRules 必須是陣列");
        }
    }

    private void validateKeyframes(JsonNode keyframes, double duration) {
        if (keyframes == null || !keyframes.isArray() || keyframes.size() < 2) {
            throw badRequest("至少需要 2 個 keyframes");
        }

        Set<Double> times = new HashSet<>();
        double maximumTime = -1;
        for (JsonNode keyframe : keyframes) {
            if (!keyframe.isObject()) {
                throw badRequest("keyframe 必須是 JSON object");
            }
            JsonNode idNode = keyframe.get("id");
            if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
                throw badRequest("keyframe.id 不可為空");
            }
            double time = requireJsonFiniteNumber(keyframe.get("time"), "keyframe.time");
            if (time < 0) {
                throw badRequest("keyframe.time 不可小於 0");
            }
            if (!times.add(time)) {
                throw badRequest("keyframe.time 不可重複");
            }
            maximumTime = Math.max(maximumTime, time);
            validateRotations(keyframe.get("jointRotations"));
        }

        if (Double.compare(maximumTime, duration) != 0) {
            throw badRequest("duration 必須等於最後一個 keyframe.time");
        }
    }

    private void validateRotations(JsonNode rotations) {
        if (rotations == null || !rotations.isObject()) {
            throw badRequest("jointRotations 必須是 JSON object");
        }
        var fields = rotations.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!JOINT_NAMES.contains(entry.getKey())) {
                throw badRequest("未知的關節類型: " + entry.getKey());
            }
            JsonNode rotation = entry.getValue();
            if (!rotation.isObject()) {
                throw badRequest("關節 rotation 必須是 JSON object");
            }
            requireJsonFiniteNumber(rotation.get("x"), entry.getKey() + ".x");
            requireJsonFiniteNumber(rotation.get("y"), entry.getKey() + ".y");
            requireJsonFiniteNumber(rotation.get("z"), entry.getKey() + ".z");
        }
    }

    private double requireJsonFiniteNumber(JsonNode node, String field) {
        if (node == null || !node.isNumber()) {
            throw badRequest(field + " 必須是數值");
        }
        double value = node.doubleValue();
        if (!Double.isFinite(value)) {
            throw badRequest(field + " 必須是有限數值");
        }
        return value;
    }

    private void requireNonNegativeFinite(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw badRequest(field + " 必須是非負有限數值");
        }
    }

    private CustomRehabExerciseDto toDto(CustomRehabExerciseEntity entity) {
        CustomRehabExerciseDto dto = new CustomRehabExerciseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCreatedByTherapistId(entity.getCreatedByTherapist().getId().toString());
        dto.setCreatedAt(entity.getCreatedAt().toString());
        dto.setUpdatedAt(entity.getUpdatedAt().toString());
        dto.setRepetitions(entity.getRepetitions());
        dto.setSets(entity.getSets());
        dto.setHoldSeconds(entity.getHoldSeconds());
        dto.setRestSeconds(entity.getRestSeconds());
        dto.setDuration(entity.getDuration());
        dto.setKeyframes(readJson(entity.getKeyframesJson()));
        dto.setEvaluationRules(readJson(entity.getEvaluationRulesJson()));
        return dto;
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw badRequest("JSON 內容無法序列化");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException error) {
            throw new CustomExerciseApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "已儲存的自訂動作 JSON 格式錯誤"
            );
        }
    }

    private CustomExerciseApiException badRequest(String message) {
        return new CustomExerciseApiException(HttpStatus.BAD_REQUEST, message);
    }

    private CustomExerciseApiException forbidden(String message) {
        return new CustomExerciseApiException(HttpStatus.FORBIDDEN, message);
    }
}
