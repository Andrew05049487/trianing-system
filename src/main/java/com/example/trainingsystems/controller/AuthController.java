package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.LoginRequest;
import com.example.trainingsystems.dto.RegisterRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public Object register(@RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return "此 Email 已被註冊";
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setName(request.getName());
        user.setRole("PATIENT");
        user.setBindingCode(generateBindingCode());

        userRepository.save(user);
        return "註冊成功";
    }

    @PostMapping("/login")
    public Object login(@RequestBody LoginRequest request) {
        User user = userRepository
                .findByEmail(request.getEmail())
                .orElse(null);

        if (user == null) {
            return "帳號不存在";
        }

        if (!user.getPassword().equals(request.getPassword())) {
            return "密碼錯誤";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "登入成功");
        result.put("userId", user.getId());
        result.put("name", user.getName());
        result.put("email", user.getEmail());
        result.put("role", user.getRole());
        result.put("bindingCode", user.getBindingCode());

        return result;
    }

    private String generateBindingCode() {
        String code;

        do {
            code = UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
        } while (userRepository.findByBindingCode(code).isPresent());

        return code;
    }
}