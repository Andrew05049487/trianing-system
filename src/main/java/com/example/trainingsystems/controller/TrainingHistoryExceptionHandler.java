package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthErrorResponse;
import com.example.trainingsystems.service.TrainingHistoryApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(assignableTypes = TrainingHistoryController.class)
public class TrainingHistoryExceptionHandler {
    @ExceptionHandler(TrainingHistoryApiException.class)
    public ResponseEntity<AuthErrorResponse> handleHistoryError(
        TrainingHistoryApiException error
    ) {
        return ResponseEntity.status(error.getStatus())
            .body(new AuthErrorResponse(error.getCode(), error.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AuthErrorResponse> handleMultipartLimit() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new AuthErrorResponse(
                "VIDEO_TOO_LARGE",
                "訓練影片超過伺服器允許大小"
            ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<AuthErrorResponse> handleMissingFile() {
        return ResponseEntity.badRequest()
            .body(new AuthErrorResponse("VIDEO_EMPTY", "請選擇有效的影片檔案"));
    }
}
