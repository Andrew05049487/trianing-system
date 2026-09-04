package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final String PATIENT_ROLE = "PATIENT";

    private final UserRepository userRepository;
    private final CustomExerciseIdentityService identityService;
    private final PasswordService passwordService;
    private final GoogleIdentityVerifier googleIdentityVerifier;

    public AuthService(
        UserRepository userRepository,
        CustomExerciseIdentityService identityService,
        PasswordService passwordService,
        GoogleIdentityVerifier googleIdentityVerifier
    ) {
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.passwordService = passwordService;
        this.googleIdentityVerifier = googleIdentityVerifier;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw badRequest("INVALID_REGISTRATION", "請輸入 Email");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw badRequest("INVALID_REGISTRATION", "請輸入密碼");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw badRequest("INVALID_REGISTRATION", "請輸入姓名");
        }

        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw badRequest("EMAIL_ALREADY_REGISTERED", "此 Email 已經註冊");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordService.encode(request.getPassword()));
        user.setName(request.getName().trim());
        user.setRole(PATIENT_ROLE);
        ensureCodes(user);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException error) {
            throw badRequest("EMAIL_ALREADY_REGISTERED", "此 Email 已經註冊");
        }
    }

    @Transactional
    public AuthLoginResponse login(LoginRequest request) {
        if (request == null || request.getIdentifier() == null || request.getPassword() == null) {
            throw badRequest("INVALID_CREDENTIALS", "請輸入帳號和密碼");
        }

        String identifier = request.getIdentifier().trim();
        User user = findByLoginIdentifier(identifier)
            .orElseThrow(this::invalidCredentials);
        PasswordService.PasswordMatch match = passwordService.verify(
            request.getPassword(),
            user.getPassword()
        );
        if (match == PasswordService.PasswordMatch.NO_MATCH) {
            throw invalidCredentials();
        }

        if (match == PasswordService.PasswordMatch.LEGACY_MATCH) {
            user.setPassword(passwordService.encode(request.getPassword()));
        }
        ensureCodes(user);
        userRepository.save(user);
        return loginResponse(user);
    }

    @Transactional
    public AuthLoginResponse googleLogin(String idToken) {
        VerifiedGoogleIdentity google = googleIdentityVerifier.verify(idToken);

        Optional<User> subjectUser = userRepository.findByGoogleSubject(google.subject());
        if (subjectUser.isPresent()) {
            User user = subjectUser.get();
            requirePatient(user);
            ensureCodes(user);
            userRepository.save(user);
            return loginResponse(user);
        }

        Optional<User> emailUser = userRepository.findByEmail(normalizeEmail(google.email()));
        if (emailUser.isPresent()) {
            User user = emailUser.get();
            requirePatient(user);
            if (user.getGoogleSubject() == null || user.getGoogleSubject().isBlank()) {
                throw new AuthApiException(
                    HttpStatus.CONFLICT,
                    "GOOGLE_LINK_REQUIRED",
                    "此 Email 已有既有帳號，請驗證原帳號密碼完成綁定"
                );
            }
            throw googleAccountConflict();
        }

        User user = new User();
        user.setEmail(normalizeEmail(google.email()));
        user.setName(google.name());
        user.setPassword(null);
        user.setGoogleSubject(google.subject());
        user.setRole(PATIENT_ROLE);
        ensureCodes(user);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException error) {
            throw googleAccountConflict();
        }
        return loginResponse(user);
    }

    @Transactional
    public AuthLoginResponse linkGoogle(String idToken, String currentPassword) {
        VerifiedGoogleIdentity google = googleIdentityVerifier.verify(idToken);
        if (currentPassword == null || currentPassword.isBlank()) {
            throw invalidCredentials();
        }

        User user = userRepository.findByEmail(normalizeEmail(google.email()))
            .orElseThrow(this::invalidCredentials);
        requirePatient(user);

        PasswordService.PasswordMatch passwordMatch = passwordService.verify(
            currentPassword,
            user.getPassword()
        );
        if (passwordMatch == PasswordService.PasswordMatch.NO_MATCH) {
            throw invalidCredentials();
        }

        Optional<User> existingSubject = userRepository.findByGoogleSubject(google.subject());
        if (existingSubject.isPresent() && !existingSubject.get().getId().equals(user.getId())) {
            throw googleAccountConflict();
        }

        String currentSubject = user.getGoogleSubject();
        if (currentSubject != null && !currentSubject.isBlank() &&
            !currentSubject.equals(google.subject())) {
            throw googleAccountConflict();
        }

        if (passwordMatch == PasswordService.PasswordMatch.LEGACY_MATCH) {
            user.setPassword(passwordService.encode(currentPassword));
        }
        user.setGoogleSubject(google.subject());
        ensureCodes(user);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException error) {
            throw googleAccountConflict();
        }
        return loginResponse(user);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeAccountId(String accountId) {
        return accountId.trim().toLowerCase(Locale.ROOT);
    }

    private Optional<User> findByLoginIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmail(normalizeEmail(identifier));
        }
        return userRepository.findByAccountId(normalizeAccountId(identifier));
    }

    private void requirePatient(User user) {
        if (!PATIENT_ROLE.equalsIgnoreCase(user.getRole())) {
            throw new AuthApiException(
                HttpStatus.FORBIDDEN,
                "GOOGLE_PATIENT_ONLY",
                "此帳號無法使用患者 Google 登入"
            );
        }
    }

    private AuthLoginResponse loginResponse(User user) {
        return new AuthLoginResponse(
            "登入成功",
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAccountId(),
            user.getRole(),
            user.getBindingCode(),
            user.getFriendCode(),
            identityService.issueToken(user),
            user.getGoogleSubject() != null && !user.getGoogleSubject().isBlank()
        );
    }

    private void ensureCodes(User user) {
        if (user.getBindingCode() == null || user.getBindingCode().isBlank()) {
            user.setBindingCode(generateUniqueBindingCode());
        }
        if (user.getFriendCode() == null || user.getFriendCode().isBlank()) {
            user.setFriendCode(generateUniqueFriendCode());
        }
    }

    private String generateUniqueBindingCode() {
        String code;
        do {
            code = randomCode();
        } while (userRepository.findByBindingCode(code).isPresent());
        return code;
    }

    private String generateUniqueFriendCode() {
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

    private AuthApiException invalidCredentials() {
        return badRequest("INVALID_CREDENTIALS", "帳號或密碼錯誤");
    }

    private AuthApiException googleAccountConflict() {
        return new AuthApiException(
            HttpStatus.CONFLICT,
            "GOOGLE_ACCOUNT_CONFLICT",
            "此 Google 帳號無法連結，請確認帳號狀態"
        );
    }

    private AuthApiException badRequest(String code, String message) {
        return new AuthApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
