package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UserJsonSecurityTest {
    @Test
    void userJsonNeverExposesPasswordHashOrGoogleSubject() throws Exception {
        User user = new User();
        user.setEmail("patient@example.com");
        user.setPassword("$2a$10$hash");
        user.setGoogleSubject("stable-google-subject");

        String json = new ObjectMapper().writeValueAsString(user);

        assertFalse(json.contains("password"));
        assertFalse(json.contains("$2a$10$hash"));
        assertFalse(json.contains("googleSubject"));
        assertFalse(json.contains("stable-google-subject"));
    }
}
