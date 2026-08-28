package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.UserSettingRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/{userId}/settings")
    public Object updateSettings(@PathVariable Long userId,
                                @RequestBody UserSettingRequest request) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return "找不到使用者";
        }

        user.setName(request.getName());
        user.setGoal(request.getGoal());
        userRepository.save(user);

        return "設定已儲存";
    }
}