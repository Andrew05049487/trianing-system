package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.ApiErrorResponse;
import com.example.trainingsystems.service.CustomExerciseApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    CustomRehabExerciseController.class,
    CustomExerciseAssignmentController.class,
    PatientCustomExerciseController.class,
    UnifiedExerciseAssignmentController.class,
    PatientAssignedExerciseController.class,
    TherapistPatientController.class,
    TrainingSessionResultController.class
})
public class CustomExerciseExceptionHandler {

    @ExceptionHandler(CustomExerciseApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiError(
        CustomExerciseApiException error
    ) {
        HttpStatus status = error.getStatus();
        return ResponseEntity
            .status(status)
            .body(ApiErrorResponse.of(status.value(), status.getReasonPhrase(), error.getMessage()));
    }

    @ExceptionHandler({
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception error) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity
            .status(status)
            .body(ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                "Request header 或 JSON 格式錯誤"
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception error) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity
            .status(status)
            .body(ApiErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                "伺服器處理自訂動作時發生錯誤"
            ));
    }
}
