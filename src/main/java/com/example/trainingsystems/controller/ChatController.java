package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.ChatContactDto;
import com.example.trainingsystems.dto.ChatConversationDto;
import com.example.trainingsystems.dto.ChatMessageDto;
import com.example.trainingsystems.dto.CreateConversationRequest;
import com.example.trainingsystems.dto.SendMessageRequest;
import com.example.trainingsystems.dto.UnreadCountDto;
import com.example.trainingsystems.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/contacts")
    public List<ChatContactDto> getContacts(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return chatService.getContacts(userId, identityToken);
    }

    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationDto> getOrCreateConversation(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @RequestBody CreateConversationRequest request
    ) {
        ChatConversationDto conversation = chatService.getOrCreateConversation(
            userId,
            identityToken,
            request == null ? null : request.otherUserId(),
            request == null ? null : request.type()
        );
        return ResponseEntity.status(HttpStatus.OK).body(conversation);
    }

    @GetMapping("/conversations")
    public List<ChatConversationDto> getConversations(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return chatService.getConversations(userId, identityToken);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageDto> getMessages(
        @PathVariable Long conversationId,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return chatService.getMessages(userId, identityToken, conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(
        @PathVariable Long conversationId,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken,
        @RequestBody SendMessageRequest request
    ) {
        ChatMessageDto message = chatService.sendMessage(
            userId,
            identityToken,
            conversationId,
            request == null ? null : request.text()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PutMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
        @PathVariable Long conversationId,
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        chatService.markAsRead(userId, identityToken, conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-counts")
    public List<UnreadCountDto> getUnreadCounts(
        @RequestHeader(value = "X-User-Id", required = false) Long userId,
        @RequestHeader(
            value = "X-Custom-Exercise-Token",
            required = false
        ) String identityToken
    ) {
        return chatService.getUnreadCounts(userId, identityToken);
    }
}
