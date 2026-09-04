package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomExerciseIdentityServiceTest {

    @Test
    void tokenCannotBeReusedForAnotherUser() {
        CustomExerciseIdentityService service = new CustomExerciseIdentityService(
            "0123456789abcdef0123456789abcdef"
        );
        User therapist = user(7L);
        User anotherTherapist = user(8L);

        String token = service.issueToken(therapist);

        assertThat(service.isValid(therapist, token)).isTrue();
        assertThat(service.isValid(anotherTherapist, token)).isFalse();
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole("THERAPIST");
        return user;
    }
}
