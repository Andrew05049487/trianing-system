package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.TrainingHistoryVideoEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.TrainingHistoryVideoRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingHistoryVideoServiceTest {
    private TrainingHistoryRepository historyRepository;
    private TrainingHistoryVideoRepository videoRepository;
    private UserRepository userRepository;
    private UserBindingRepository bindingRepository;
    private CustomExerciseIdentityService identityService;
    private TrainingHistoryVideoService service;
    private User patient;

    @BeforeEach
    void setUp() {
        historyRepository = mock(TrainingHistoryRepository.class);
        videoRepository = mock(TrainingHistoryVideoRepository.class);
        userRepository = mock(UserRepository.class);
        bindingRepository = mock(UserBindingRepository.class);
        identityService = mock(CustomExerciseIdentityService.class);
        service = new TrainingHistoryVideoService(
            historyRepository,
            videoRepository,
            userRepository,
            bindingRepository,
            identityService,
            4L
        );
        patient = user(7L, "PATIENT");
        TrainingHistoryEntity history = new TrainingHistoryEntity();
        history.setId(55L);
        history.setUser(patient);
        when(historyRepository.findById(55L)).thenReturn(Optional.of(history));
    }

    @Test
    void uploadCreatesOneRowAndPreservesUnicodeFileName() {
        MockMultipartFile file = video("病患伸手.mp4", new byte[] {1, 2, 3});
        when(videoRepository.findById(55L)).thenReturn(Optional.empty());
        when(videoRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.upload(55L, 7L, file);

        verify(videoRepository).save(any(TrainingHistoryVideoEntity.class));
    }

    @Test
    void duplicateUploadReplacesExistingRowInsteadOfAddingAnother() {
        TrainingHistoryVideoEntity existing = new TrainingHistoryVideoEntity();
        existing.setHistoryId(55L);
        existing.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        existing.setVideoData(new byte[] {9});
        when(videoRepository.findById(55L)).thenReturn(Optional.of(existing));

        service.upload(55L, 7L, video("new.mp4", new byte[] {1, 2}));

        assertArrayEquals(new byte[] {1, 2}, existing.getVideoData());
        assertThat(existing.getCreatedAt())
            .isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
        assertThat(existing.getUpdatedAt()).isNotNull();
        verify(videoRepository).save(existing);
    }

    @Test
    void uploadRejectsMissingHistoryNonVideoAndOversizedFile() {
        when(historyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(
            99L,
            7L,
            video("a.mp4", new byte[] {1})
        )).isInstanceOfSatisfying(TrainingHistoryApiException.class,
            error -> assertThat(error.getStatus().value()).isEqualTo(404));

        MockMultipartFile text = new MockMultipartFile(
            "file", "bad.txt", "text/plain", new byte[] {1}
        );
        assertThatThrownBy(() -> service.upload(55L, 7L, text))
            .isInstanceOfSatisfying(TrainingHistoryApiException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(415));

        assertThatThrownBy(() -> service.upload(
            55L,
            7L,
            video("large.mp4", new byte[] {1, 2, 3, 4, 5})
        )).isInstanceOfSatisfying(TrainingHistoryApiException.class,
            error -> assertThat(error.getStatus().value()).isEqualTo(413));
    }

    @Test
    void uploadRejectsWrongHistoryOwner() {
        assertThatThrownBy(() -> service.upload(
            55L,
            8L,
            video("a.mp4", new byte[] {1})
        )).isInstanceOfSatisfying(TrainingHistoryApiException.class,
            error -> assertThat(error.getStatus().value()).isEqualTo(403));
    }

    @Test
    void boundTherapistCanReadButUnboundTherapistCannot() {
        User therapist = user(9L, "THERAPIST");
        TrainingHistoryVideoEntity video = new TrainingHistoryVideoEntity();
        video.setHistoryId(55L);
        video.setContentType("video/mp4");
        video.setFileName("訓練.mp4");
        video.setVideoData(new byte[] {1, 2, 3});
        when(userRepository.findById(9L)).thenReturn(Optional.of(therapist));
        when(identityService.isConfigured()).thenReturn(true);
        when(identityService.isValid(therapist, "token")).thenReturn(true);
        when(videoRepository.findById(55L)).thenReturn(Optional.of(video));
        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                7L,
                9L,
                "THERAPIST"
            )).thenReturn(true);

        assertArrayEquals(
            new byte[] {1, 2, 3},
            service.read(55L, 9L, "token").bytes()
        );

        when(bindingRepository
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                7L,
                9L,
                "THERAPIST"
            )).thenReturn(false);
        assertThatThrownBy(() -> service.read(55L, 9L, "token"))
            .isInstanceOfSatisfying(TrainingHistoryApiException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(403));
    }

    private MockMultipartFile video(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "video/mp4", bytes);
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
