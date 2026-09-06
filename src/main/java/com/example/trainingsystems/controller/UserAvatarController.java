package com.example.trainingsystems.controller;

import com.example.trainingsystems.service.UserAvatarService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class UserAvatarController {
    private final UserAvatarService service;

    public UserAvatarController(UserAvatarService service) {
        this.service = service;
    }

    @PutMapping(
        value = "/account/avatar",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> uploadCurrentUserAvatar(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @RequestPart("file") MultipartFile file
    ) {
        service.uploadCurrentUserAvatar(userId, identityToken, file);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(
        value = "/account/avatar/google",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Void> uploadGoogleAvatar(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @RequestPart("file") MultipartFile file
    ) {
        service.uploadGoogleAvatar(userId, identityToken, file);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{targetUserId}/avatar")
    public ResponseEntity<byte[]> getUserAvatar(
        @RequestHeader(value = "X-User-Id", required = false) Long viewerUserId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @PathVariable Long targetUserId
    ) {
        UserAvatarService.AvatarContent avatar = service.getUserAvatar(
            viewerUserId,
            identityToken,
            targetUserId
        );
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(avatar.mimeType()))
            .contentLength(avatar.bytes().length)
            .cacheControl(CacheControl.noStore())
            .body(avatar.bytes());
    }
}
