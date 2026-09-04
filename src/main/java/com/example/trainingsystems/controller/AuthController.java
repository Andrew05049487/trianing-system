package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.GoogleAccountLinkRequest;
import com.example.trainingsystems.dto.GoogleAuthRequest;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * 註冊
     *
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
        @RequestBody RegisterRequest request
    ) {
        authService.register(request);
        return ResponseEntity.ok("註冊成功");
    }

    /*
     * 登入
     *
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(
        @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthLoginResponse> googleLogin(
        @RequestBody GoogleAuthRequest request
    ) {
        return ResponseEntity.ok(authService.googleLogin(request.idToken()));
    }

    @PostMapping("/google/link")
    public ResponseEntity<AuthLoginResponse> linkGoogle(
        @RequestBody GoogleAccountLinkRequest request
    ) {
        return ResponseEntity.ok(
            authService.linkGoogle(request.idToken(), request.currentPassword())
        );
    }
}
