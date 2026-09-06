package com.example.trainingsystems.service;

import org.springframework.http.HttpStatus;

public class ChatApiException extends RuntimeException {
    private final HttpStatus status;

    public ChatApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
