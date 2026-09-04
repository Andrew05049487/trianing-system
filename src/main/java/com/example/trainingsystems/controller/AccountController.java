package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AccountDeleteRequest;
import com.example.trainingsystems.dto.AccountIdUpdateRequest;
import com.example.trainingsystems.dto.AccountInfoResponse;
import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.AuthenticatedGoogleLinkRequest;
import com.example.trainingsystems.dto.PasswordUpdateRequest;
import com.example.trainingsystems.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountInfoResponse getCurrentAccount(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken
    ) {
        return accountService.getAccountInfo(userId, identityToken);
    }

    @PutMapping("/account-id")
    public AccountInfoResponse updateAccountId(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @RequestBody AccountIdUpdateRequest request
    ) {
        return accountService.updateAccountId(userId, identityToken, request.accountId());
    }

    @PutMapping("/password")
    public AccountInfoResponse updatePassword(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @RequestBody PasswordUpdateRequest request
    ) {
        return accountService.updatePassword(
            userId,
            identityToken,
            request.currentPassword(),
            request.newPassword()
        );
    }

    @PostMapping("/google/link")
    public AuthLoginResponse linkGoogle(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @RequestBody AuthenticatedGoogleLinkRequest request
    ) {
        return accountService.linkGoogle(userId, identityToken, request.idToken());
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Custom-Exercise-Token") String identityToken,
        @RequestBody AccountDeleteRequest request
    ) {
        accountService.deleteAccount(
            userId,
            identityToken,
            request.currentPassword(),
            request.idToken()
        );
        return ResponseEntity.noContent().build();
    }
}
