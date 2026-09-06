package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthErrorResponse;
import com.example.trainingsystems.service.AvatarApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = UserAvatarController.class)
public class UserAvatarExceptionHandler {
    @ExceptionHandler(AvatarApiException.class)
    public ResponseEntity<AuthErrorResponse> handleAvatarError(
        AvatarApiException error
    ) {
        return ResponseEntity
            .status(error.getStatus())
            .body(new AuthErrorResponse(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AuthErrorResponse> handleMultipartLimit() {
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new AuthErrorResponse(
                "AVATAR_TOO_LARGE",
                "頭像不可超過 5 MB"
            ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<AuthErrorResponse> handleMissingFile() {
        return ResponseEntity
            .badRequest()
            .body(new AuthErrorResponse(
                "AVATAR_EMPTY",
                "請選擇有效的圖片檔案"
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AuthErrorResponse> handleUnexpected() {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new AuthErrorResponse(
                "AVATAR_INTERNAL_ERROR",
                "頭像服務暫時無法使用，請稍後再試"
            ));
    }
}
