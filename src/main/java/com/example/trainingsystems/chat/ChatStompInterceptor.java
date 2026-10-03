package com.example.trainingsystems.chat;

import com.example.trainingsystems.service.ChatService;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class ChatStompInterceptor implements ChannelInterceptor {
    private final ChatService chats;
    public ChatStompInterceptor(ChatService chats) { this.chats = chats; }

    private record ChatPrincipal(Long id, String token) implements Principal {
        @Override public String getName() { return id.toString(); }
        @Override public String toString() { return "AuthenticatedChatUser"; }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message; // heartbeat: no DB query
        var command = headers.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            String token = headers.getFirstNativeHeader("X-Custom-Exercise-Token");
            headers.removeNativeHeader("X-Custom-Exercise-Token");
            try {
                Long id = Long.valueOf(headers.getFirstNativeHeader("X-User-Id"));
                chats.authenticateRealtime(id, token);
                headers.setUser(new ChatPrincipal(id, token));
                // Never retain credentials in native headers that Spring diagnostics may serialize.
            } catch (RuntimeException ignored) { throw denied(); }
        } else if (command == StompCommand.SUBSCRIBE) {
            if (!(headers.getUser() instanceof ChatPrincipal user)
                    || !ChatRealtimeEvent.SUBSCRIPTION.equals(headers.getDestination())) throw denied();
            chats.authenticateRealtime(user.id(), user.token());
        } else if (command == StompCommand.SEND) {
            throw denied(); // Chat writes exclusively use REST + existing authorization.
        } else if (command != StompCommand.DISCONNECT && command != StompCommand.UNSUBSCRIBE) {
            throw denied();
        }
        return message;
    }

    private IllegalArgumentException denied() { return new IllegalArgumentException("Chat connection denied"); }
}
