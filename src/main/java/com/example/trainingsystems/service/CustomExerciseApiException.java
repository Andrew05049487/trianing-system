package com.example.trainingsystems.service;

import org.springframework.http.HttpStatus;

public class CustomExerciseApiException extends RuntimeException {
    private final HttpStatus status;

    public CustomExerciseApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
