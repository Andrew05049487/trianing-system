package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.User;
import com.example.trainingsystems.entity.UserAvatarEntity;
import com.example.trainingsystems.repository.FriendshipRepository;
import com.example.trainingsystems.repository.UserAvatarRepository;
import com.example.trainingsystems.repository.UserBindingRepository;
import com.example.trainingsystems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarServiceTest {
    private UserRepository users;
    private UserAvatarRepository avatars;
    private UserBindingRepository bindings;
    private FriendshipRepository friendships;
    private CustomExerciseIdentityService identity;
    private UserAvatarService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        avatars = mock(UserAvatarRepository.class);
        bindings = mock(UserBindingRepository.class);
        friendships = mock(FriendshipRepository.class);
        identity = mock(CustomExerciseIdentityService.class);
        service = new UserAvatarService(
            users,
            avatars,
            bindings,
            friendships,
            identity
        );
        when(identity.isConfigured()).thenReturn(true);
    }

    @Test
    void authenticatedUserCanUploadSupportedImageTypes() {
        User patient = user(1L, "PATIENT");
        authenticate(patient);
        when(avatars.findById(1L)).thenReturn(Optional.empty());

        upload("image/jpeg", jpeg());
        upload("image/png", png());
        upload("image/webp", webp());

        verify(avatars, org.mockito.Mockito.times(3)).save(any(UserAvatarEntity.class));
    }

    @Test
    void emptyUnsupportedMismatchedAndOversizedFilesAreRejected() {
        User patient = user(1L, "PATIENT");
        authenticate(patient);

        assertStatus(
            HttpStatus.BAD_REQUEST,
            () -> upload("image/jpeg", new byte[0])
        );
        assertStatus(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            () -> upload("image/gif", new byte[] {'G', 'I', 'F'})
        );
        assertStatus(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            () -> upload("image/png", jpeg())
        );
        assertStatus(
            HttpStatus.PAYLOAD_TOO_LARGE,
            () -> upload(
                "image/jpeg",
                new byte[(int) UserAvatarService.MAX_IMAGE_BYTES + 1]
            )
        );
        verify(avatars, never()).save(any());
    }

    @Test
    void secondUploadReplacesTheSingleAvatarForAuthenticatedUser() {
        User patient = user(1L, "PATIENT");
        authenticate(patient);
        UserAvatarEntity existing = avatar(1L, "image/jpeg", jpeg());
        when(avatars.findById(1L))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        upload("image/jpeg", jpeg());
        upload("image/png", png());

        assertEquals("image/png", existing.getMimeType());
        assertArrayEquals(png(), existing.getImageData());
        assertEquals(1L, existing.getUserId());
    }

    @Test
    void uploadNeverAcceptsAnotherTargetUserAndInvalidTokenIsRejected() {
        User patient = user(1L, "PATIENT");
        when(users.findById(1L)).thenReturn(Optional.of(patient));
        when(identity.isValid(patient, "bad-token")).thenReturn(false);

        AvatarApiException error = assertThrows(
            AvatarApiException.class,
            () -> service.uploadCurrentUserAvatar(
                1L,
                "bad-token",
                file("image/jpeg", jpeg())
            )
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(avatars, never()).save(any());
    }

    @Test
    void userCanReadOwnAvatarWithStoredMimeType() {
        User patient = user(1L, "PATIENT");
        authenticate(patient);
        when(avatars.findById(1L)).thenReturn(Optional.of(
            avatar(1L, "image/png", png())
        ));

        UserAvatarService.AvatarContent result = service.getUserAvatar(
            1L,
            "token",
            1L
        );

        assertEquals("image/png", result.mimeType());
        assertArrayEquals(png(), result.bytes());
    }

    @Test
    void boundPatientAndTherapistCanReadEachOthersAvatar() {
        User patient = user(1L, "PATIENT");
        User therapist = user(2L, "THERAPIST");
        authenticate(patient);
        authenticate(therapist);
        when(bindings
            .existsByPatient_IdAndLinkedUser_IdAndRelationshipIgnoreCase(
                1L,
                2L,
                "THERAPIST"
            )).thenReturn(true);
        when(avatars.findById(1L)).thenReturn(Optional.of(
            avatar(1L, "image/jpeg", jpeg())
        ));
        when(avatars.findById(2L)).thenReturn(Optional.of(
            avatar(2L, "image/png", png())
        ));

        assertEquals(
            "image/png",
            service.getUserAvatar(1L, "token", 2L).mimeType()
        );
        assertEquals(
            "image/jpeg",
            service.getUserAvatar(2L, "token", 1L).mimeType()
        );
    }

    @Test
    void patientFriendsCanReadEachOthersAvatar() {
        User viewer = user(1L, "PATIENT");
        User friend = user(3L, "PATIENT");
        authenticate(viewer);
        when(users.findById(3L)).thenReturn(Optional.of(friend));
        when(friendships.existsByUserLowIdAndUserHighId(1L, 3L))
            .thenReturn(true);
        when(avatars.findById(3L)).thenReturn(Optional.of(
            avatar(3L, "image/webp", webp())
        ));

        assertEquals(
            "image/webp",
            service.getUserAvatar(1L, "token", 3L).mimeType()
        );
    }

    @Test
    void unrelatedUserIsForbiddenAndMissingAvatarIsNotFound() {
        User viewer = user(1L, "THERAPIST");
        User unrelated = user(2L, "PATIENT");
        authenticate(viewer);
        when(users.findById(2L)).thenReturn(Optional.of(unrelated));

        assertStatus(
            HttpStatus.FORBIDDEN,
            () -> service.getUserAvatar(1L, "token", 2L)
        );

        User self = user(1L, "THERAPIST");
        when(users.findById(1L)).thenReturn(Optional.of(self));
        when(avatars.findById(1L)).thenReturn(Optional.empty());
        assertStatus(
            HttpStatus.NOT_FOUND,
            () -> service.getUserAvatar(1L, "token", 1L)
        );
    }

    private void upload(String mimeType, byte[] data) {
        service.uploadCurrentUserAvatar(1L, "token", file(mimeType, data));
    }

    private MockMultipartFile file(String mimeType, byte[] data) {
        return new MockMultipartFile("file", "avatar", mimeType, data);
    }

    private void authenticate(User user) {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(identity.isValid(user, "token")).thenReturn(true);
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEmail("user" + id + "@example.com");
        return user;
    }

    private UserAvatarEntity avatar(Long userId, String mime, byte[] data) {
        UserAvatarEntity avatar = new UserAvatarEntity();
        avatar.setUserId(userId);
        avatar.setMimeType(mime);
        avatar.setImageData(data);
        return avatar;
    }

    private byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    }

    private byte[] png() {
        return new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
        };
    }

    private byte[] webp() {
        return new byte[] {
            'R', 'I', 'F', 'F', 0, 0, 0, 0,
            'W', 'E', 'B', 'P'
        };
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        AvatarApiException error = assertThrows(
            AvatarApiException.class,
            action::run
        );
        assertEquals(status, error.getStatus());
    }
}
