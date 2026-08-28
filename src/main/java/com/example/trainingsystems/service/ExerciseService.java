package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.ExerciseResultRequest;
import com.example.trainingsystems.entity.Exercise;
import com.example.trainingsystems.entity.ExerciseResult;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.ExerciseRepository;
import com.example.trainingsystems.repository.ExerciseResultRepository;
import com.example.trainingsystems.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ExerciseService {

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExerciseResultRepository resultRepository;

    @Autowired
    private UserRepository userRepository;

    public List<Exercise> getExerciseList() {
        return exerciseRepository.findAll();
    }

    public ExerciseResult saveResult(ExerciseResultRequest request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("找不到使用者"));

        Exercise exercise =
                exerciseRepository.findById(request.getExerciseId())
                        .orElseThrow(() ->
                                new RuntimeException("找不到訓練項目"));

        ExerciseResult result = new ExerciseResult();

        result.setUser(user);
        result.setExercise(exercise);
        result.setRepCount(request.getRepCount());

        result.setAccuracy(
                request.getAccuracy() != null
                        ? request.getAccuracy()
                        : BigDecimal.ZERO
        );

        result.setProgress(
                request.getProgress() != null
                        ? request.getProgress()
                        : BigDecimal.ZERO
        );

        result.setSpeedState(request.getSpeedState());

        result.setIsComplete(
                request.getIsComplete() != null
                        ? request.getIsComplete()
                        : false
        );

        return resultRepository.save(result);
    }
}