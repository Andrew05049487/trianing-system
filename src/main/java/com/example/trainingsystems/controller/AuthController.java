package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.service.AuthApiException;
import com.example.trainingsystems.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * 一般註冊
     *
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(
        @RequestBody RegisterRequest request
    ) {
        authService.register(request);

        // 保留 Flutter 現有的回傳格式。
        return ResponseEntity.ok("註冊成功");
    }

    /*
     * 使用 Email 或 accountId 登入
     *
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(
        @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
            authService.login(request)
        );
    }

    /*
     * Google 登入
     *
     * POST /api/auth/google
     *
     * JSON:
     * {
     *   "idToken": "Google ID Token"
     * }
     */
    @PostMapping("/google")
    public ResponseEntity<AuthLoginResponse> googleLogin(
        @RequestBody Map<String, String> request
    ) {
        return ResponseEntity.ok(
            authService.googleLogin(request.get("idToken"))
        );
    }

    /*
     * 將 Google 帳號綁定至既有帳號
     *
     * POST /api/auth/google/link
     *
     * JSON:
     * {
     *   "idToken": "Google ID Token",
     *   "password": "原本帳號密碼"
     * }
     */
    @PostMapping("/google/link")
    public ResponseEntity<AuthLoginResponse> linkGoogle(
        @RequestBody Map<String, String> request
    ) {
        return ResponseEntity.ok(
            authService.linkGoogle(
                request.get("idToken"),
                request.get("password")
            )
        );
    }

    /*
     * 統一處理登入與註冊錯誤。
     */
    @ExceptionHandler(AuthApiException.class)
    public ResponseEntity<Map<String, Object>> handleAuthError(
        AuthApiException error
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", error.getCode());
        response.put("message", error.getMessage());

        return ResponseEntity
            .status(error.getStatus())
            .body(response);
    }
}