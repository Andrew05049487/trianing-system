package com.example.trainingsystems.service;

import com.example.trainingsystems.dto.UserSettingRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User updateSettings(Long userId, UserSettingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("找不到使用者"));

        user.setName(request.getName());
        user.setGoal(request.getGoal());

        return userRepository.save(user);
    }
}