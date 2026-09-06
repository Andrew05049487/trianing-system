package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.TherapistRegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TherapistRegistrationService {
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final CustomExerciseIdentityService identityService;
    private final AuthAbuseRateLimiter rateLimiter;

    public TherapistRegistrationService(
        UserRepository userRepository,
        PasswordService passwordService,
        CustomExerciseIdentityService identityService,
        AuthAbuseRateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.identityService = identityService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public AuthLoginResponse register(
        TherapistRegisterRequest request,
        String sourceKey
    ) {
        String safeSourceKey = sourceKey == null ? "unknown" : sourceKey;
        if (!rateLimiter.allowTherapistRegistrationRequest(safeSourceKey)) {
            throw invalidRegistration(HttpStatus.TOO_MANY_REQUESTS);
        }
        if (request == null) {
            throw invalidRegistration();
        }

        String email = AuthService.normalizeEmail(request.email());
        String name = request.name() == null ? null : request.name().trim();
        if (email == null || email.isBlank() || name == null || name.isBlank() ||
            !passwordService.isAcceptableNewPassword(request.password())) {
            throw invalidRegistration();
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw invalidRegistration();
        }

        User user = new User();
        user.setEmail(email);
        user.setName(name);
        user.setPassword(passwordService.encode(request.password()));
        user.setRole("THERAPIST");

        try {
            User saved = userRepository.saveAndFlush(user);
            return new AuthLoginResponse(
                "治療師註冊成功",
                saved.getId(),
                saved.getName(),
                saved.getEmail(),
                saved.getAccountId(),
                saved.getRole(),
                saved.getBindingCode(),
                saved.getFriendCode(),
                identityService.issueToken(saved),
                false
            );
        } catch (DataIntegrityViolationException error) {
            throw invalidRegistration();
        }
    }

    private AuthApiException invalidRegistration() {
        return invalidRegistration(HttpStatus.BAD_REQUEST);
    }

    private AuthApiException invalidRegistration(HttpStatus status) {
        return new AuthApiException(
            status,
            "INVALID_THERAPIST_REGISTRATION",
            "治療師註冊資訊無效"
        );
    }
}
