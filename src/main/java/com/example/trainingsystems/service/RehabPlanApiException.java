package com.example.trainingsystems.service;

import org.springframework.http.HttpStatus;

public class RehabPlanApiException extends RuntimeException {
    private final HttpStatus status;

    public RehabPlanApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
