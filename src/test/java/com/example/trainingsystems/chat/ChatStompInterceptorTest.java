package com.example.trainingsystems.chat;

import com.example.trainingsystems.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatStompInterceptorTest {
    private final ChatService chats = mock(ChatService.class);
    private final ChatStompInterceptor interceptor = new ChatStompInterceptor(chats);
    private StompHeaderAccessor connect() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("X-User-Id", "1");
        headers.setNativeHeader("X-Custom-Exercise-Token", "synthetic-fixture");
        apply(headers);
        return headers;
    }
    private void apply(StompHeaderAccessor headers) {
        headers.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }
    @Test void authenticatedConnectCreatesPrincipalAndRemovesNativeToken() {
        var headers = connect();
        assertThat(headers.getUser().getName()).isEqualTo("1");
        assertThat(headers.getUser().toString()).doesNotContain("synthetic-fixture");
        assertThat(headers.getFirstNativeHeader("X-Custom-Exercise-Token")).isNull();
        verify(chats).authenticateRealtime(1L, "synthetic-fixture");
    }
    @Test void invalidIdentityIsDeniedWithoutLeakingCause() {
        doThrow(new IllegalArgumentException("sensitive-provider-detail")).when(chats).authenticateRealtime(1L, "synthetic-fixture");
        assertThatThrownBy(this::connect).hasMessage("Chat connection denied").hasNoCause();
    }
    @Test void onlyOwnGenericPrivateSubscriptionIsAllowedAndIdentityReloaded() {
        var subscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscription.setUser(connect().getUser()); subscription.setDestination(ChatRealtimeEvent.SUBSCRIPTION);
        apply(subscription);
        verify(chats, times(2)).authenticateRealtime(1L, "synthetic-fixture");
    }
    @Test void otherUserPublicAndBrokerResolvedDestinationsAreDenied() {
        var principal = connect().getUser();
        for (String destination : new String[] {"/user/2/queue/chat-events", "/topic/chat", "/queue/chat-events-user123"}) {
            var subscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            subscription.setUser(principal); subscription.setDestination(destination);
            assertThatThrownBy(() -> apply(subscription)).hasMessage("Chat connection denied");
        }
    }
    @Test void anonymousSubscribeAndAllSendAreDenied() {
        var subscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscription.setDestination(ChatRealtimeEvent.SUBSCRIPTION);
        assertThatThrownBy(() -> apply(subscription)).hasMessage("Chat connection denied");
        var send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setUser(connect().getUser()); send.setDestination("/app/chat");
        assertThatThrownBy(() -> apply(send)).hasMessage("Chat connection denied");
    }
    @Test void deletedAccountCannotSubscribeAgain() {
        var principal = connect().getUser();
        doThrow(new IllegalArgumentException("deleted")).when(chats).authenticateRealtime(any(), any());
        var subscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscription.setUser(principal); subscription.setDestination(ChatRealtimeEvent.SUBSCRIPTION);
        assertThatThrownBy(() -> apply(subscription)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void heartbeatsNeverQueryDatabase() {
        apply(StompHeaderAccessor.createForHeartbeat()); verifyNoInteractions(chats);
    }
}
