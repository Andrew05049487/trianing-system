package com.example.trainingsystems.service;

import org.springframework.http.HttpStatus;

public class AvatarApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AvatarApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
