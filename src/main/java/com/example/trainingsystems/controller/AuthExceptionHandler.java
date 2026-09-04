package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthErrorResponse;
import com.example.trainingsystems.service.AuthApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AuthController.class, AccountController.class})
public class AuthExceptionHandler {
    @ExceptionHandler(AuthApiException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthError(AuthApiException error) {
        return ResponseEntity
            .status(error.getStatus())
            .body(new AuthErrorResponse(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthErrorResponse> handleMalformedRequest() {
        return ResponseEntity
            .badRequest()
            .body(new AuthErrorResponse("INVALID_REQUEST", "請確認輸入資料格式"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<AuthErrorResponse> handleMissingIdentityHeader() {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(new AuthErrorResponse("UNAUTHORIZED", "登入狀態已失效"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<AuthErrorResponse> handleConstraintConflict() {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new AuthErrorResponse(
                "ACCOUNT_CONFLICT",
                "帳號資料已存在，請重新登入"
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthErrorResponse> handleUnexpected() {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new AuthErrorResponse(
                "AUTH_INTERNAL_ERROR",
                "登入服務暫時無法使用，請稍後再試"
            ));
    }
}
