package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.FriendRequestCreateDto;
import com.example.trainingsystems.dto.FriendRequestRespondDto;
import com.example.trainingsystems.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@CrossOrigin(origins = "*")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/requests")
    public ResponseEntity<?> sendRequest(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @RequestBody(required = false) FriendRequestCreateDto requestDto
    ) {
        return ResponseEntity.ok(
            friendService.sendRequest(userId, identityToken, requestDto)
        );
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<?> getPendingRequests(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return ResponseEntity.ok(
            friendService.getPendingRequests(userId, identityToken)
        );
    }

    @GetMapping("/requests/sent")
    public ResponseEntity<?> getSentRequests(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return ResponseEntity.ok(
            friendService.getSentRequests(userId, identityToken)
        );
    }

    @PutMapping("/requests/{requestId}/respond")
    public ResponseEntity<?> respondToRequest(
        @PathVariable Long requestId,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @RequestBody(required = false) FriendRequestRespondDto responseDto
    ) {
        return ResponseEntity.ok(
            friendService.respondToRequest(
                userId,
                identityToken,
                requestId,
                responseDto
            )
        );
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> cancelRequest(
        @PathVariable Long requestId,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return ResponseEntity.ok(
            friendService.cancelRequest(userId, identityToken, requestId)
        );
    }

    @GetMapping
    public ResponseEntity<?> getFriends(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return ResponseEntity.ok(
            friendService.getFriends(userId, identityToken)
        );
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<?> removeFriend(
        @PathVariable Long friendId,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return ResponseEntity.ok(
            friendService.removeFriend(userId, identityToken, friendId)
        );
    }
}
