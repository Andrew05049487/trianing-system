package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.ExerciseResultRequest;
import com.example.trainingsystems.entity.Exercise;
import com.example.trainingsystems.entity.ExerciseResult;
import com.example.trainingsystems.repository.ExerciseResultRepository;
import com.example.trainingsystems.service.ExerciseService;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercise")
@CrossOrigin(origins = "*")
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final ExerciseResultRepository exerciseResultRepository;

    public ExerciseController(
            ExerciseService exerciseService,
            ExerciseResultRepository exerciseResultRepository) {

        this.exerciseService = exerciseService;
        this.exerciseResultRepository = exerciseResultRepository;
    }

    @GetMapping("/list")
    public List<Exercise> getExerciseList() {
        return exerciseService.getExerciseList();
    }

    @PostMapping("/result")
    public Map<String, Object> saveResult(
            @RequestBody ExerciseResultRequest request) {

        ExerciseResult savedResult =
                exerciseService.saveResult(request);

        Map<String, Object> response = new HashMap<>();

        response.put("message", "練習結果已儲存");
        response.put("resultId", savedResult.getId());
        response.put("userId", savedResult.getUser().getId());
        response.put(
                "exerciseId",
                savedResult.getExercise().getId()
        );
        response.put("repCount", savedResult.getRepCount());
        response.put("accuracy", savedResult.getAccuracy());
        response.put("progress", savedResult.getProgress());
        response.put("speedState", savedResult.getSpeedState());
        response.put("isComplete", savedResult.getIsComplete());

        return response;
    }

    @GetMapping("/history/{userId}")
    public List<ExerciseResult> getHistory(
            @PathVariable Long userId) {

        return exerciseResultRepository.findByUserId(userId);
    }

    @GetMapping("/report/{userId}")
    public Map<String, Object> getReport(
            @PathVariable Long userId) {

        List<ExerciseResult> results =
                exerciseResultRepository.findByUserId(userId);

        int totalExercises = results.size();

        int totalRepCount = results.stream()
                .map(ExerciseResult::getRepCount)
                .filter(count -> count != null)
                .mapToInt(Integer::intValue)
                .sum();

        double averageAccuracy = results.stream()
                .filter(result -> result.getAccuracy() != null)
                .mapToDouble(
                        result ->
                                result.getAccuracy().doubleValue()
                )
                .average()
                .orElse(0.0);

        Map<String, Object> report = new HashMap<>();

        report.put("totalExercises", totalExercises);
        report.put("totalRepCount", totalRepCount);
        report.put("averageAccuracy", averageAccuracy);

        return report;
    }
}