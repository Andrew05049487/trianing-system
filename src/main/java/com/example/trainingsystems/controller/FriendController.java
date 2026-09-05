package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.FriendRequestCreateDto;
import com.example.trainingsystems.dto.FriendRequestRespondDto;
import com.example.trainingsystems.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@CrossOrigin(origins = "*")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /*
     * 送出好友邀請
     *
     * POST /api/friends/requests
     */
    @PostMapping("/requests")
    public ResponseEntity<?> sendRequest(
        @RequestBody FriendRequestCreateDto requestDto
    ) {
        try {
            return ResponseEntity.ok(
                friendService.sendRequest(requestDto)
            );
        } catch (IllegalArgumentException exception) {
            return badRequest(exception);
        }
    }

    /*
     * 查看尚未處理的好友邀請
     *
     * GET /api/friends/requests/pending/{receiverId}
     */
    @GetMapping("/requests/pending/{receiverId}")
    public ResponseEntity<?> getPendingRequests(
        @PathVariable Long receiverId
    ) {
        try {
            return ResponseEntity.ok(
                friendService.getPendingRequests(receiverId)
            );
        } catch (IllegalArgumentException exception) {
            return badRequest(exception);
        }
    }

    /*
     * 接受或拒絕好友邀請
     *
     * PUT /api/friends/requests/{requestId}/respond
     */
    @PutMapping("/requests/{requestId}/respond")
    public ResponseEntity<?> respondToRequest(
        @PathVariable Long requestId,
        @RequestBody FriendRequestRespondDto responseDto
    ) {
        try {
            return ResponseEntity.ok(
                friendService.respondToRequest(
                    requestId,
                    responseDto
                )
            );
        } catch (IllegalArgumentException exception) {
            return badRequest(exception);
        }
    }

    /*
     * 取消自己送出的待處理邀請
     *
     * DELETE /api/friends/requests/{requestId}?senderId=1
     */
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> cancelRequest(
        @PathVariable Long requestId,
        @RequestParam Long senderId
    ) {
        try {
            return ResponseEntity.ok(
                friendService.cancelRequest(
                    requestId,
                    senderId
                )
            );
        } catch (IllegalArgumentException exception) {
            return badRequest(exception);
        }
    }

    /*
     * 取得使用者的好友列表
     *
     * GET /api/friends/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getFriends(
        @PathVariable Long userId
    ) {
        try {
            return ResponseEntity.ok(
                friendService.getFriends(userId)
            );
        } catch (IllegalArgumentException exception) {
            return badRequest(exception);
        }
    }

    /*
     * 刪除好友
     *
     * DELETE /api/friends/{userId}/{friendId}
     */
    @DeleteMapping("/{userId}/{friendId}")
    public ResponseEntity<?> removeFriend(
        @PathVariable Long userId,
        @PathVariable Long friendId
    ) {
        try {
            return ResponseEntity.ok(
                friendService.removeFriend(
                    userId,
                    friendId
                )
            );
        } catch (IllegalArgumentException exception) {
            return badRequest(exception);
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(
        IllegalArgumentException exception
    ) {
        return ResponseEntity
            .badRequest()
            .body(Map.of(
                "message",
                exception.getMessage()
            ));
    }
}