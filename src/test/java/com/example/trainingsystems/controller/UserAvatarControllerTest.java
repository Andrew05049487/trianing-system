package com.example.trainingsystems.controller;

import com.example.trainingsystems.service.UserAvatarService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarControllerTest {
    private final UserAvatarService service = mock(UserAvatarService.class);
    private final UserAvatarController controller = new UserAvatarController(service);

    @Test
    void uploadDelegatesAuthenticatedIdentityAndMultipartFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.jpg",
            "image/jpeg",
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        );

        ResponseEntity<Void> response = controller.uploadCurrentUserAvatar(
            1L,
            "token",
            file
        );

        assertEquals(204, response.getStatusCode().value());
        verify(service).uploadCurrentUserAvatar(1L, "token", file);
    }

    @Test
    void getReturnsRawBytesWithStoredContentType() {
        byte[] bytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        when(service.getUserAvatar(2L, "token", 1L)).thenReturn(
            new UserAvatarService.AvatarContent(bytes, "image/png")
        );

        ResponseEntity<byte[]> response = controller.getUserAvatar(
            2L,
            "token",
            1L
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertArrayEquals(bytes, response.getBody());
    }
}
