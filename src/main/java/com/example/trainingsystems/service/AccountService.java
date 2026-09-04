package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.AccountInfoResponse;
import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.entity.CustomRehabExerciseEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.CustomExerciseAssignmentRepository;
import com.example.trainingsystems.repository.CustomRehabExerciseRepository;
import com.example.trainingsystems.repository.ExerciseAssignmentRepository;
import com.example.trainingsystems.repository.ExerciseResultRepository;
import com.example.trainingsystems.repository.FriendRequestRepository;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AccountService {
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,20}$");
    private static final String PATIENT_ROLE = "PATIENT";

    private final UserRepository userRepository;
    private final CustomExerciseIdentityService identityService;
    private final PasswordService passwordService;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final CustomExerciseAssignmentRepository customAssignmentRepository;
    private final CustomRehabExerciseRepository customExerciseRepository;
    private final ExerciseAssignmentRepository exerciseAssignmentRepository;
    private final ExerciseResultRepository exerciseResultRepository;
    private final UserBindingRepository userBindingRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;

    public AccountService(
        UserRepository userRepository,
        CustomExerciseIdentityService identityService,
        PasswordService passwordService,
        GoogleIdentityVerifier googleIdentityVerifier,
        CustomExerciseAssignmentRepository customAssignmentRepository,
        CustomRehabExerciseRepository customExerciseRepository,
        ExerciseAssignmentRepository exerciseAssignmentRepository,
        ExerciseResultRepository exerciseResultRepository,
        UserBindingRepository userBindingRepository,
        FriendRequestRepository friendRequestRepository,
        FriendshipRepository friendshipRepository
    ) {
        this.userRepository = userRepository;
        this.identityService = identityService;
        this.passwordService = passwordService;
        this.googleIdentityVerifier = googleIdentityVerifier;
        this.customAssignmentRepository = customAssignmentRepository;
        this.customExerciseRepository = customExerciseRepository;
        this.exerciseAssignmentRepository = exerciseAssignmentRepository;
        this.exerciseResultRepository = exerciseResultRepository;
        this.userBindingRepository = userBindingRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional(readOnly = true)
    public AccountInfoResponse getAccountInfo(Long userId, String identityToken) {
        return accountInfo(authenticatedUser(userId, identityToken));
    }

    @Transactional
    public AccountInfoResponse updateAccountId(
        Long userId,
        String identityToken,
        String requestedAccountId
    ) {
        User user = authenticatedUser(userId, identityToken);
        if (requestedAccountId == null || !ACCOUNT_ID_PATTERN.matcher(requestedAccountId).matches()) {
            throw badRequest(
                "INVALID_ACCOUNT_ID",
                "帳號 ID 僅能使用 4～20 個英文字母與數字"
            );
        }

        String normalized = AuthService.normalizeAccountId(requestedAccountId);
        Optional<User> owner = userRepository.findByAccountId(normalized);
        if (owner.isPresent() && !owner.get().getId().equals(user.getId())) {
            throw conflict("ACCOUNT_ID_ALREADY_IN_USE", "此帳號 ID 已被使用");
        }

        user.setAccountId(normalized);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException error) {
            throw conflict("ACCOUNT_ID_ALREADY_IN_USE", "此帳號 ID 已被使用");
        }
        return accountInfo(user);
    }

    @Transactional
    public AccountInfoResponse updatePassword(
        Long userId,
        String identityToken,
        String currentPassword,
        String newPassword
    ) {
        User user = authenticatedUser(userId, identityToken);
        if (newPassword == null || newPassword.length() < 6) {
            throw badRequest("INVALID_PASSWORD", "新密碼至少需要 6 個字元");
        }

        if (user.getPassword() != null &&
            passwordService.verify(currentPassword, user.getPassword()) ==
                PasswordService.PasswordMatch.NO_MATCH) {
            throw invalidCredentials();
        }

        user.setPassword(passwordService.encode(newPassword));
        userRepository.saveAndFlush(user);
        return accountInfo(user);
    }

    @Transactional
    public AuthLoginResponse linkGoogle(
        Long userId,
        String identityToken,
        String idToken
    ) {
        User user = authenticatedUser(userId, identityToken);
        requirePatient(user);
        VerifiedGoogleIdentity google = googleIdentityVerifier.verify(idToken);

        Optional<User> subjectOwner = userRepository.findByGoogleSubject(google.subject());
        if (subjectOwner.isPresent() && !subjectOwner.get().getId().equals(user.getId())) {
            throw googleAccountConflict();
        }

        String verifiedEmail = AuthService.normalizeEmail(google.email());
        Optional<User> emailOwner = userRepository.findByEmail(verifiedEmail);
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(user.getId())) {
            throw conflict(
                "GOOGLE_EMAIL_ALREADY_IN_USE",
                "此 Google 電子郵件已綁定其他帳號，無法使用。"
            );
        }

        String existingSubject = user.getGoogleSubject();
        if (existingSubject != null && !existingSubject.isBlank() &&
            !existingSubject.equals(google.subject())) {
            throw googleAccountConflict();
        }

        user.setGoogleSubject(google.subject());
        user.setEmail(verifiedEmail);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException error) {
            throw googleAccountConflict();
        }
        return loginResponse(user);
    }

    @Transactional
    public void deleteAccount(
        Long userId,
        String identityToken,
        String currentPassword,
        String idToken
    ) {
        User user = authenticatedUser(userId, identityToken);
        verifyFreshDeletionProof(user, currentPassword, idToken);

        Long id = user.getId();
        customAssignmentRepository.deleteAll(
            customAssignmentRepository.findAllByPatient_IdOrAssignedByTherapist_Id(id, id)
        );

        List<CustomRehabExerciseEntity> ownedExercises =
            customExerciseRepository.findAllByCreatedByTherapist_Id(id, Sort.unsorted());
        for (CustomRehabExerciseEntity exercise : ownedExercises) {
            customAssignmentRepository.deleteAllByCustomExercise_Id(exercise.getId());
        }
        customExerciseRepository.deleteAll(ownedExercises);

        exerciseAssignmentRepository.deleteAll(
            exerciseAssignmentRepository.findAllByPatient_IdOrAssignedByTherapist_Id(id, id)
        );
        exerciseResultRepository.deleteAll(exerciseResultRepository.findByUserId(id));
        userBindingRepository.deleteAll(userBindingRepository.findByPatient_IdOrLinkedUser_Id(id, id));
        friendRequestRepository.deleteAll(friendRequestRepository.findBySenderIdOrReceiverId(id, id));
        friendshipRepository.deleteAll(friendshipRepository.findByUserLowIdOrUserHighId(id, id));
        userRepository.delete(user);
        userRepository.flush();
    }

    private User authenticatedUser(Long userId, String identityToken) {
        if (userId == null) {
            throw unauthorized();
        }
        User user = userRepository.findById(userId).orElseThrow(this::unauthorized);
        if (!identityService.isValid(user, identityToken)) {
            throw unauthorized();
        }
        return user;
    }

    private void verifyFreshDeletionProof(
        User user,
        String currentPassword,
        String idToken
    ) {
        if (user.getPassword() != null) {
            if (passwordService.verify(currentPassword, user.getPassword()) ==
                PasswordService.PasswordMatch.NO_MATCH) {
                throw invalidCredentials();
            }
            return;
        }

        VerifiedGoogleIdentity google = googleIdentityVerifier.verify(idToken);
        if (user.getGoogleSubject() == null ||
            !user.getGoogleSubject().equals(google.subject())) {
            throw invalidCredentials();
        }
    }

    private AccountInfoResponse accountInfo(User user) {
        boolean googleLinked = user.getGoogleSubject() != null &&
            !user.getGoogleSubject().isBlank();
        return new AccountInfoResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAccountId(),
            user.getRole(),
            user.getBindingCode(),
            user.getFriendCode(),
            googleLinked,
            googleLinked ? user.getEmail() : null,
            user.getPassword() != null
        );
    }

    private AuthLoginResponse loginResponse(User user) {
        return new AuthLoginResponse(
            "帳號已更新",
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getAccountId(),
            user.getRole(),
            user.getBindingCode(),
            user.getFriendCode(),
            identityService.issueToken(user),
            true
        );
    }

    private void requirePatient(User user) {
        if (!PATIENT_ROLE.equalsIgnoreCase(user.getRole())) {
            throw new AuthApiException(
                HttpStatus.FORBIDDEN,
                "GOOGLE_PATIENT_ONLY",
                "此帳號無法使用患者 Google 綁定"
            );
        }
    }

    private AuthApiException unauthorized() {
        return new AuthApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登入狀態已失效");
    }

    private AuthApiException invalidCredentials() {
        return badRequest("INVALID_CREDENTIALS", "帳號或密碼錯誤");
    }

    private AuthApiException googleAccountConflict() {
        return conflict("GOOGLE_ACCOUNT_CONFLICT", "此 Google 帳號無法連結，請確認帳號狀態");
    }

    private AuthApiException badRequest(String code, String message) {
        return new AuthApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private AuthApiException conflict(String code, String message) {
        return new AuthApiException(HttpStatus.CONFLICT, code, message);
    }
}
