package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.TrainingHistoryRequest;
import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.TrainingHistoryVideoRepository;
import com.example.trainingsystems.repository.UserRepository;
import com.example.trainingsystems.service.TrainingHistoryVideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingHistoryControllerTest {
    private TrainingHistoryRepository historyRepository;
    private TrainingHistoryVideoRepository videoRepository;
    private UserRepository userRepository;
    private TrainingHistoryVideoService videoService;
    private TrainingHistoryController controller;

    @BeforeEach
    void setUp() {
        historyRepository = mock(TrainingHistoryRepository.class);
        videoRepository = mock(TrainingHistoryVideoRepository.class);
        userRepository = mock(UserRepository.class);
        videoService = mock(TrainingHistoryVideoService.class);
        controller = new TrainingHistoryController(
            historyRepository,
            videoRepository,
            userRepository,
            new ObjectMapper(),
            videoService
        );
    }

    @Test
    void saveReturnsIdCompletedRepsAndUnicodeWithoutTranscoding() {
        User user = user(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(historyRepository.findByUser_IdAndClientTimestamp(
            7L,
            "2026-09-09 16:25:44"
        )).thenReturn(Optional.empty());
        when(historyRepository.save(any())).thenAnswer(call -> {
            TrainingHistoryEntity entity = call.getArgument(0);
            entity.setId(123L);
            return entity;
        });
        when(videoRepository.existsById(123L)).thenReturn(false);

        TrainingHistoryRequest request = new TrainingHistoryRequest();
        request.setUserId(7L);
        request.setClientTimestamp("2026-09-09 16:25:44");
        request.setActionName("伸手舉高訓練");
        request.setDifficulty(2);
        request.setDurationSeconds(9);
        request.setCompletedReps(1);
        request.setTargetReps(1);
        request.setMistakeLogs(List.of("第1次：側捏動作不夠流暢"));

        ResponseEntity<Map<String, Object>> response = controller.save(request);

        assertEquals(201, response.getStatusCode().value());
        assertThat(response.getBody())
            .containsEntry("id", 123L)
            .containsEntry("actionName", "伸手舉高訓練")
            .containsEntry("completedReps", 1)
            .containsEntry("targetReps", 1)
            .containsEntry("hasVideo", false);
        assertEquals(
            List.of("第1次：側捏動作不夠流暢"),
            response.getBody().get("mistakeLogs")
        );
    }

    @Test
    void saveUpsertsExistingMetadataInsteadOfCreatingDuplicate() {
        User user = user(7L);
        TrainingHistoryEntity existing = history(55L, user);
        LocalDateTime originalCreatedAt = existing.getCreatedAt();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(historyRepository.findByUser_IdAndClientTimestamp(7L, "same-ts"))
            .thenReturn(Optional.of(existing));
        when(historyRepository.save(existing)).thenReturn(existing);

        TrainingHistoryRequest request = new TrainingHistoryRequest();
        request.setUserId(7L);
        request.setClientTimestamp("same-ts");
        request.setActionName("翻掌訓練");
        request.setCompletedReps(2);
        request.setTargetReps(5);

        ResponseEntity<Map<String, Object>> response = controller.save(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(55L, response.getBody().get("id"));
        assertEquals(originalCreatedAt, existing.getCreatedAt());
        verify(historyRepository).save(existing);
    }

    @Test
    void historyAddsVideoMetadataWithoutLoadingBinaryRows() {
        User user = user(7L);
        TrainingHistoryEntity entity = history(55L, user);
        when(historyRepository.findByUser_IdOrderByCreatedAtDesc(7L))
            .thenReturn(List.of(entity));
        when(videoRepository.findExistingHistoryIds(List.of(55L)))
            .thenReturn(List.of(55L));

        List<Map<String, Object>> response = controller.history(7L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0))
            .containsEntry("hasVideo", true)
            .containsEntry("videoUrl", "/api/training-history/55/video")
            .containsEntry("completedReps", 2);
    }

    @Test
    void uploadVideoDelegatesOwnerAndMultipartFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "訓練.mp4", "video/mp4", new byte[] {1, 2, 3}
        );

        ResponseEntity<Void> response = controller.uploadVideo(55L, 7L, file);

        assertEquals(204, response.getStatusCode().value());
        verify(videoService).upload(55L, 7L, file);
    }

    @Test
    void readVideoSupportsPartialAndSuffixRanges() {
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5};
        when(videoService.read(55L, 9L, "token")).thenReturn(
            new TrainingHistoryVideoService.VideoContent(
                bytes,
                "video/mp4",
                "患者訓練.mp4"
            )
        );

        ResponseEntity<byte[]> partial = controller.readVideo(
            55L, "bytes=2-4", 9L, "token"
        );
        ResponseEntity<byte[]> suffix = controller.readVideo(
            55L, "bytes=-2", 9L, "token"
        );

        assertEquals(206, partial.getStatusCode().value());
        assertEquals("bytes", partial.getHeaders().getFirst("Accept-Ranges"));
        assertEquals("bytes 2-4/6", partial.getHeaders().getFirst("Content-Range"));
        assertArrayEquals(new byte[] {2, 3, 4}, partial.getBody());
        assertArrayEquals(new byte[] {4, 5}, suffix.getBody());
    }

    @Test
    void readVideoRejectsUnsatisfiableRangeWith416() {
        when(videoService.read(55L, 9L, "token")).thenReturn(
            new TrainingHistoryVideoService.VideoContent(
                new byte[] {0, 1, 2},
                "video/mp4",
                "video.mp4"
            )
        );

        ResponseEntity<byte[]> response = controller.readVideo(
            55L, "bytes=99-100", 9L, "token"
        );

        assertEquals(416, response.getStatusCode().value());
        assertEquals("bytes */3", response.getHeaders().getFirst("Content-Range"));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole("PATIENT");
        return user;
    }

    private TrainingHistoryEntity history(Long id, User user) {
        TrainingHistoryEntity entity = new TrainingHistoryEntity();
        entity.setId(id);
        entity.setUser(user);
        entity.setActionName("伸手舉高訓練");
        entity.setDifficulty(2);
        entity.setDurationSeconds(9);
        entity.setCompletedReps(2);
        entity.setTargetReps(5);
        entity.setMistakeCount(0);
        entity.setMistakeLogs("[]");
        entity.setClientTimestamp("same-ts");
        entity.setCreatedAt(LocalDateTime.of(2026, 9, 9, 16, 25));
        return entity;
    }
}
