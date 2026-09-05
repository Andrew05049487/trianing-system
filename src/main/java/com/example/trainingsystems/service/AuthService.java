package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CustomExerciseIdentityService identityService;
    private final PasswordService passwordService;
    private final GoogleIdentityVerifier googleVerifier;

    public AuthService(
        UserRepository userRepository,
        CustomExerciseIdentityService identityService,
        PasswordService passwordService,
        GoogleIdentityVerifier googleVerifier
    ) {
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.passwordService = passwordService;
        this.googleVerifier = googleVerifier;
    }

    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String password = request.getPassword();

        if (email == null || email.isBlank()) {
            throw new AuthApiException(
                HttpStatus.BAD_REQUEST,
                "EMAIL_REQUIRED",
                "請輸入 Email"
            );
        }

        if (password == null || password.isBlank()) {
            throw new AuthApiException(
                HttpStatus.BAD_REQUEST,
                "PASSWORD_REQUIRED",
                "請輸入密碼"
            );
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new AuthApiException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "此 Email 已經註冊"
            );
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordService.encode(password));
        user.setName(request.getName());
        user.setRole("PATIENT");
        user.setBindingCode(generateBindingCode());
        user.setFriendCode(generateFriendCode());

        return userRepository.saveAndFlush(user);
    }

    public AuthLoginResponse login(LoginRequest request) {
        String identifier = request.getIdentifier();
        String password = request.getPassword();

        if (identifier == null || identifier.isBlank() ||
            password == null || password.isBlank()) {
            throw invalidCredentials();
        }

        User user;

        if (identifier.contains("@")) {
            String email = normalizeEmail(identifier);

            user = userRepository.findByEmail(email)
                .orElseThrow(this::invalidCredentials);
        } else {
            String accountId = normalizeAccountId(identifier);

            user = userRepository.findByAccountId(accountId)
                .orElseThrow(this::invalidCredentials);
        }

        PasswordService.PasswordMatch match =
            passwordService.verify(password, user.getPassword());

        if (match == PasswordService.PasswordMatch.NO_MATCH) {
            throw invalidCredentials();
        }

        // 舊版明文密碼登入成功後，自動升級成 BCrypt。
        if (match == PasswordService.PasswordMatch.LEGACY_MATCH) {
            user.setPassword(passwordService.encode(password));
            userRepository.save(user);
        }

        return createLoginResponse(user, "登入成功");
    }

    public AuthLoginResponse googleLogin(String idToken) {
        VerifiedGoogleIdentity googleIdentity =
            googleVerifier.verify(idToken);

        String subject = googleIdentity.subject();
        String email = normalizeEmail(googleIdentity.email());

        Optional<User> subjectOwner =
            userRepository.findByGoogleSubject(subject);

        if (subjectOwner.isPresent()) {
            User user = subjectOwner.get();
            requirePatient(user);

            return createLoginResponse(user, "Google 登入成功");
        }

        Optional<User> emailOwner =
            userRepository.findByEmail(email);

        if (emailOwner.isPresent()) {
            User user = emailOwner.get();

            requirePatient(user);

            throw new AuthApiException(
                HttpStatus.CONFLICT,
                "GOOGLE_LINK_REQUIRED",
                "此 Email 已有帳號，請先使用密碼登入並綁定 Google 帳號"
            );
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(null);
        user.setName(googleIdentity.name());
        user.setRole("PATIENT");
        user.setGoogleSubject(subject);
        user.setBindingCode(generateBindingCode());
        user.setFriendCode(generateFriendCode());

        try {
            User savedUser = userRepository.saveAndFlush(user);

            return createLoginResponse(
                savedUser,
                "Google 登入成功"
            );
        } catch (DataIntegrityViolationException error) {
            throw googleAccountConflict();
        }
    }

    public AuthLoginResponse linkGoogle(
        String idToken,
        String password
    ) {
        VerifiedGoogleIdentity googleIdentity =
            googleVerifier.verify(idToken);

        String subject = googleIdentity.subject();
        String email = normalizeEmail(googleIdentity.email());

        User user = userRepository.findByEmail(email)
            .orElseThrow(this::invalidCredentials);

        requirePatient(user);

        if (user.getGoogleSubject() != null &&
            !user.getGoogleSubject().isBlank() &&
            !user.getGoogleSubject().equals(subject)) {
            throw googleAccountConflict();
        }

        Optional<User> subjectOwner =
            userRepository.findByGoogleSubject(subject);

        if (subjectOwner.isPresent() &&
            !subjectOwner.get().getId().equals(user.getId())) {
            throw googleAccountConflict();
        }

        PasswordService.PasswordMatch match =
            passwordService.verify(password, user.getPassword());

        if (match == PasswordService.PasswordMatch.NO_MATCH) {
            throw invalidCredentials();
        }

        if (match == PasswordService.PasswordMatch.LEGACY_MATCH) {
            user.setPassword(passwordService.encode(password));
        }

        user.setGoogleSubject(subject);

        try {
            User savedUser = userRepository.saveAndFlush(user);

            return createLoginResponse(
                savedUser,
                "Google 帳號綁定成功"
            );
        } catch (DataIntegrityViolationException error) {
            throw googleAccountConflict();
        }
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeAccountId(String accountId) {
        if (accountId == null) {
            return null;
        }

        return accountId.trim().toLowerCase(Locale.ROOT);
    }

    private AuthLoginResponse createLoginResponse(
        User user,
        String message
    ) {
        return new AuthLoginResponse(
            message,
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAccountId(),
            user.getRole(),
            user.getBindingCode(),
            user.getFriendCode(),
            identityService.issueToken(user),
            user.getGoogleSubject() != null &&
                !user.getGoogleSubject().isBlank()
        );
    }

    private void requirePatient(User user) {
        if (user.getRole() == null ||
            !"PATIENT".equalsIgnoreCase(user.getRole())) {
            throw new AuthApiException(
                HttpStatus.FORBIDDEN,
                "GOOGLE_PATIENT_ONLY",
                "目前只有病患帳號可以使用 Google 登入"
            );
        }
    }

    private AuthApiException invalidCredentials() {
        return new AuthApiException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "帳號或密碼錯誤"
        );
    }

    private AuthApiException googleAccountConflict() {
        return new AuthApiException(
            HttpStatus.CONFLICT,
            "GOOGLE_ACCOUNT_CONFLICT",
            "Google 帳號已經被其他使用者使用"
        );
    }

    private String generateBindingCode() {
        String code;

        do {
            code = randomCode();
        } while (userRepository.findByBindingCode(code).isPresent());

        return code;
    }

    private String generateFriendCode() {
        String code;

        do {
            code = randomCode();
        } while (userRepository.findByFriendCode(code).isPresent());

        return code;
    }

    private String randomCode() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8)
            .toUpperCase(Locale.ROOT);
    }
}