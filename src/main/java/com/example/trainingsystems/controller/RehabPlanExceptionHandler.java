package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.ApiErrorResponse;
import com.example.trainingsystems.service.RehabPlanApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = RehabPlanController.class)
public class RehabPlanExceptionHandler {
    @ExceptionHandler(RehabPlanApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiError(
        RehabPlanApiException error
    ) {
        HttpStatus status = error.getStatus();
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            error.getMessage()
        ));
    }

    @ExceptionHandler({
        MissingRequestHeaderException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception error) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            "Request parameters or JSON format are invalid"
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
        DataIntegrityViolationException error
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            "A rehabilitation plan already exists for this patient and date"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception error) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
            status.value(),
            status.getReasonPhrase(),
            "An unexpected error occurred while processing the rehabilitation plan"
        ));
    }
}
