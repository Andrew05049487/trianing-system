package com.example.trainingsystems.service;

import com.example.trainingsystems.controller.BindingController;
import com.example.trainingsystems.dto.BindingRequest;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserBinding;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyBindingControllerSecurityTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBindingRepository bindingRepository;

    @Test
    void legacyEndpointCannotCreateTherapistBindingWithoutHmac() {
        BindingController controller = new BindingController(
            userRepository,
            bindingRepository
        );
        BindingRequest request = new BindingRequest();
        request.setLinkedUserId(7L);
        request.setBindingCode("ABC12345");
        request.setRelationship("THERAPIST");

        ResponseEntity<?> response = controller.bind(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userRepository, never()).findByBindingCode(any());
        verify(bindingRepository, never()).save(any());
    }

    @Test
    void legacyFamilyBindingFlowRemainsAvailable() {
        BindingController controller = new BindingController(
            userRepository,
            bindingRepository
        );
        User patient = user(15L, "PATIENT");
        User family = user(9L, "PATIENT");
        BindingRequest request = new BindingRequest();
        request.setLinkedUserId(9L);
        request.setBindingCode("ABC12345");
        request.setRelationship("FAMILY");
        when(userRepository.findByBindingCode("ABC12345"))
            .thenReturn(Optional.of(patient));
        when(userRepository.findById(9L)).thenReturn(Optional.of(family));
        when(bindingRepository.existsByPatient_IdAndLinkedUser_Id(15L, 9L))
            .thenReturn(false);

        ResponseEntity<?> response = controller.bind(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<UserBinding> binding =
            ArgumentCaptor.forClass(UserBinding.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getRelationship()).isEqualTo("FAMILY");
        assertThat(family.getRole()).isEqualTo("FAMILY");
        verify(userRepository).save(family);
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setName("User " + id);
        return user;
    }
}
