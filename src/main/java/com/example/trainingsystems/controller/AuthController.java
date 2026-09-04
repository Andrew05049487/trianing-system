package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import com.example.trainingsystems.service.CustomExerciseIdentityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final CustomExerciseIdentityService customExerciseIdentityService;

    public AuthController(
        UserRepository userRepository,
        CustomExerciseIdentityService customExerciseIdentityService
    ) {
        this.userRepository = userRepository;
        this.customExerciseIdentityService = customExerciseIdentityService;
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
        if (request.getEmail() == null ||
            request.getEmail().isBlank()) {
            return ResponseEntity
                .badRequest()
                .body("請輸入 Email");
        }

        if (request.getPassword() == null ||
            request.getPassword().isBlank()) {
            return ResponseEntity
                .badRequest()
                .body("請輸入密碼");
        }

        if (request.getName() == null ||
            request.getName().isBlank()) {
            return ResponseEntity
                .badRequest()
                .body("請輸入姓名");
        }

        String email = request
            .getEmail()
            .trim()
            .toLowerCase(Locale.ROOT);

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity
                .badRequest()
                .body("此 Email 已經註冊");
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(request.getPassword());
        user.setRole("PATIENT");

        // 帳號綁定功能使用
        user.setBindingCode(generateUniqueBindingCode());

        // 加好友功能使用
        user.setFriendCode(generateUniqueFriendCode());

        userRepository.save(user);

        return ResponseEntity.ok("註冊成功");
    }

    /*
     * 登入
     *
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
        @RequestBody LoginRequest request
    ) {
        if (request.getEmail() == null ||
            request.getPassword() == null) {
            return ResponseEntity
                .badRequest()
                .body("請輸入 Email 和密碼");
        }

        String email = request
            .getEmail()
            .trim()
            .toLowerCase(Locale.ROOT);

        Optional<User> optionalUser =
            userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                .badRequest()
                .body("帳號或密碼錯誤");
        }

        User user = optionalUser.get();

        if (!user.getPassword().equals(request.getPassword())) {
            return ResponseEntity
                .badRequest()
                .body("帳號或密碼錯誤");
        }

        /*
         * 若舊帳號沒有代碼，登入時自動補上。
         */
        boolean needsSave = false;

        if (user.getBindingCode() == null ||
            user.getBindingCode().isBlank()) {
            user.setBindingCode(generateUniqueBindingCode());
            needsSave = true;
        }

        if (user.getFriendCode() == null ||
            user.getFriendCode().isBlank()) {
            user.setFriendCode(generateUniqueFriendCode());
            needsSave = true;
        }

        if (needsSave) {
            userRepository.save(user);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "登入成功");
        result.put("userId", user.getId());
        result.put("name", user.getName());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        result.put("bindingCode", user.getBindingCode());
        result.put("friendCode", user.getFriendCode());
        result.put(
            "customExerciseToken",
            customExerciseIdentityService.issueToken(user)
        );

        return ResponseEntity.ok(result);
    }

    /*
     * 產生不重複的帳號綁定碼。
     */
    private String generateUniqueBindingCode() {
        String code;

        do {
            code = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        } while (
            userRepository.findByBindingCode(code).isPresent()
        );

        return code;
    }

    /*
     * 產生不重複的好友代碼。
     */
    private String generateUniqueFriendCode() {
        String code;

        do {
            code = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        } while (
            userRepository.findByFriendCode(code).isPresent()
        );

        return code;
    }
}