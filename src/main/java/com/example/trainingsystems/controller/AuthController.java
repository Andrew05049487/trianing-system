package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.AuthMessageResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.PasswordForgotRequest;
import com.example.trainingsystems.dto.PasswordResetRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.dto.TherapistRegisterRequest;
import com.example.trainingsystems.service.AuthApiException;
import com.example.trainingsystems.service.AuthService;
import com.example.trainingsystems.service.PasswordResetService;
import com.example.trainingsystems.service.TherapistRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final TherapistRegistrationService therapistRegistrationService;

    public AuthController(
        AuthService authService,
        PasswordResetService passwordResetService,
        TherapistRegistrationService therapistRegistrationService
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.therapistRegistrationService = therapistRegistrationService;
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
     *   "currentPassword": "原本帳號密碼"
     * }
     */
    @PostMapping("/google/link")
    public ResponseEntity<AuthLoginResponse> linkGoogle(
        @RequestBody Map<String, String> request
    ) {
        return ResponseEntity.ok(
            authService.linkGoogle(
                request.get("idToken"),
                request.get("currentPassword") != null
                    ? request.get("currentPassword")
                    : request.get("password")
            )
        );
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<AuthMessageResponse> forgotPassword(
        @RequestBody PasswordForgotRequest request
    ) {
        passwordResetService.requestReset(
            request == null ? null : request.identifier()
        );
        return ResponseEntity.ok(
            new AuthMessageResponse(PasswordResetService.GENERIC_FORGOT_MESSAGE)
        );
    }

    @PostMapping("/password/reset")
    public ResponseEntity<AuthMessageResponse> resetPassword(
        @RequestBody PasswordResetRequest request
    ) {
        passwordResetService.resetPassword(
            request == null ? null : request.identifier(),
            request == null ? null : request.code(),
            request == null ? null : request.newPassword()
        );
        return ResponseEntity.ok(
            new AuthMessageResponse("密碼已更新，請使用新密碼登入")
        );
    }

    @PostMapping("/therapist/register")
    public ResponseEntity<AuthLoginResponse> registerTherapist(
        @RequestBody TherapistRegisterRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
            therapistRegistrationService.register(
                request,
                httpRequest.getRemoteAddr()
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
