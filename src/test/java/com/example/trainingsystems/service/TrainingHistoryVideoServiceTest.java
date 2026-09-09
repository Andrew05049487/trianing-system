package com.example.trainingsystems.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import com.example.trainingsystems.entity.TrainingHistoryEntity;
import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.repository.TrainingHistoryRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;

class TrainingHistoryVideoServiceTest {

    private TrainingHistoryRepository historyRepository;
    private UserRepository userRepository;
    private UserBindingRepository bindingRepository;
    private CustomExerciseIdentityService identityService;
    private JdbcTemplate jdbcTemplate;

    private TrainingHistoryVideoService service;

    private User patient;


    @BeforeEach
    void setUp() {

        historyRepository =
            mock(TrainingHistoryRepository.class);

        userRepository =
            mock(UserRepository.class);

        bindingRepository =
            mock(UserBindingRepository.class);

        identityService =
            mock(CustomExerciseIdentityService.class);

        jdbcTemplate =
            mock(JdbcTemplate.class);


        service =
            new TrainingHistoryVideoService(
                historyRepository,
                userRepository,
                bindingRepository,
                identityService,
                jdbcTemplate,
                4L
            );


        patient =
            user(
                7L,
                "PATIENT"
            );


        TrainingHistoryEntity history =
            new TrainingHistoryEntity();

        history.setId(55L);
        history.setUser(patient);


        when(
            historyRepository.findById(55L)
        ).thenReturn(
            Optional.of(history)
        );
    }


    @Test
    void uploadCreatesVideoThroughJdbcStreaming() {

        MockMultipartFile file =
            video(
                "病患伸手.mp4",
                new byte[] {1, 2, 3}
            );


        // 0 = DB 還沒有這筆影片
        when(
            jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(55L)
            )
        ).thenReturn(0);


        when(
            jdbcTemplate.update(
                any(PreparedStatementCreator.class)
            )
        ).thenReturn(1);


        service.upload(
            55L,
            7L,
            file
        );


        verify(
            jdbcTemplate
        ).update(
            any(PreparedStatementCreator.class)
        );
    }


    @Test
    void duplicateUploadReplacesExistingRowInsteadOfAddingAnother() {

        // 1 = DB 已經有同 historyId 的影片
        when(
            jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(55L)
            )
        ).thenReturn(1);


        when(
            jdbcTemplate.update(
                any(PreparedStatementCreator.class)
            )
        ).thenReturn(1);


        service.upload(
            55L,
            7L,
            video(
                "new.mp4",
                new byte[] {1, 2}
            )
        );


        verify(
            jdbcTemplate
        ).update(
            any(PreparedStatementCreator.class)
        );
    }


    @Test
    void uploadRejectsMissingHistoryNonVideoAndOversizedFile() {

        when(
            historyRepository.findById(99L)
        ).thenReturn(
            Optional.empty()
        );


        assertThatThrownBy(
            () ->
                service.upload(
                    99L,
                    7L,
                    video(
                        "a.mp4",
                        new byte[] {1}
                    )
                )
        ).isInstanceOfSatisfying(
            TrainingHistoryApiException.class,
            error ->
                assertThat(
                    error.getStatus().value()
                ).isEqualTo(404)
        );


        MockMultipartFile text =
            new MockMultipartFile(
                "file",
                "bad.txt",
                "text/plain",
                new byte[] {1}
            );


        assertThatThrownBy(
            () ->
                service.upload(
                    55L,
                    7L,
                    text
                )
        ).isInstanceOfSatisfying(
            TrainingHistoryApiException.class,
            error ->
                assertThat(
                    error.getStatus().value()
                ).isEqualTo(415)
        );


        assertThatThrownBy(
            () ->
                service.upload(
                    55L,
                    7L,
                    video(
                        "large.mp4",
                        new byte[] {
                            1, 2, 3, 4, 5
                        }
                    )
                )
        ).isInstanceOfSatisfying(
            TrainingHistoryApiException.class,
            error ->
                assertThat(
                    error.getStatus().value()
                ).isEqualTo(413)
        );
    }


    @Test
    void uploadRejectsWrongHistoryOwner() {

        assertThatThrownBy(
            () ->
                service.upload(
                    55L,
                    8L,
                    video(
                        "a.mp4",
                        new byte[] {1}
                    )
                )
        ).isInstanceOfSatisfying(
            TrainingHistoryApiException.class,
            error ->
                assertThat(
                    error.getStatus().value()
                ).isEqualTo(403)
        );
    }


    @Test
    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    void boundTherapistCanReadMetadataButUnboundTherapistCannot() {

        User therapist =
            user(
                9L,
                "THERAPIST"
            );


        when(
            userRepository.findById(9L)
        ).thenReturn(
            Optional.of(therapist)
        );


        when(
            identityService.isConfigured()
        ).thenReturn(true);


        when(
            identityService.isValid(
                therapist,
                "token"
            )
        ).thenReturn(true);


        when(
            bindingRepository
                .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                    7L,
                    9L,
                    "THERAPIST"
                )
        ).thenReturn(true);


        TrainingHistoryVideoService.VideoMetadata metadata =
            new TrainingHistoryVideoService.VideoMetadata(
                "訓練.mp4",
                "video/mp4",
                3L
            );


        doReturn(
            List.of(metadata)
        ).when(
            jdbcTemplate
        ).query(
            anyString(),
            any(RowMapper.class),
            any(Object[].class)
        );


        TrainingHistoryVideoService.VideoMetadata result =
            service.requireReadableVideo(
                55L,
                9L,
                "token"
            );


        assertThat(
            result.fileName()
        ).isEqualTo(
            "訓練.mp4"
        );


        assertThat(
            result.contentType()
        ).isEqualTo(
            "video/mp4"
        );


        assertThat(
            result.fileSize()
        ).isEqualTo(
            3L
        );


        when(
            bindingRepository
                .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                    7L,
                    9L,
                    "THERAPIST"
                )
        ).thenReturn(false);


        assertThatThrownBy(
            () ->
                service.requireReadableVideo(
                    55L,
                    9L,
                    "token"
                )
        ).isInstanceOfSatisfying(
            TrainingHistoryApiException.class,
            error ->
                assertThat(
                    error.getStatus().value()
                ).isEqualTo(403)
        );
    }


    @Test
    void readRangeReturnsOnlyRequestedChunk() {

        byte[] expected =
            new byte[] {
                2, 3, 4
            };


        doReturn(
            expected
        ).when(
            jdbcTemplate
        ).queryForObject(
            anyString(),
            eq(byte[].class),
            any(Object[].class)
        );


        byte[] actual =
            service.readRange(
                55L,
                2L,
                3
            );


        assertArrayEquals(
            expected,
            actual
        );
    }


    private MockMultipartFile video(
        String name,
        byte[] bytes
    ) {

        return new MockMultipartFile(
            "file",
            name,
            "video/mp4",
            bytes
        );
    }


    private User user(
        Long id,
        String role
    ) {

        User user =
            new User();

        user.setId(id);
        user.setRole(role);

        return user;
    }
}