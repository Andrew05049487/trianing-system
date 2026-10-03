package com.example.trainingsystems.chat;

import com.example.trainingsystems.service.ChatService;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual TCP WebSocket + Spring broker on random local port; identity repository is mocked. */
@SpringBootTest(classes = ChatWebSocketIntegrationTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {
    @org.springframework.context.annotation.Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    @Import({ChatWebSocketConfiguration.class, ChatStompInterceptor.class})
    static class App { }
    @MockBean ChatService chats;
    @Autowired SimpMessagingTemplate broker;
    @LocalServerPort int port;
    WebSocketStompClient client;
    ThreadPoolTaskScheduler scheduler;
    @BeforeEach void setup() {
        scheduler = new ThreadPoolTaskScheduler(); scheduler.initialize();
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setTaskScheduler(scheduler); client.setMessageConverter(new MappingJackson2MessageConverter());
        doThrow(new IllegalArgumentException("Invalid identity")).when(chats).authenticateRealtime(any(), eq("invalid"));
    }
    @AfterEach void stop() { client.stop(); scheduler.shutdown(); }
    private StompSession connect(long id, String token) throws Exception {
        var headers = new StompHeaders();
        headers.add("X-User-Id", Long.toString(id)); headers.add("X-Custom-Exercise-Token", token);
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws/chat", new org.springframework.web.socket.WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }
    private BlockingQueue<ChatRealtimeEvent> subscribe(StompSession session) throws Exception {
        var queue = new LinkedBlockingQueue<ChatRealtimeEvent>();
        var ready = new LinkedBlockingQueue<Boolean>();
        session.subscribe(ChatRealtimeEvent.SUBSCRIPTION, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return ChatRealtimeEvent.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                var event = (ChatRealtimeEvent) payload;
                if (event.type().equals("CONNECTION_READY")) ready.add(true); else queue.add(event);
            }
        });
        assertThat(ready.poll(5, TimeUnit.SECONDS)).isTrue();
        return queue;
    }
    @Test void authenticatedPrivateEventsReachBothParticipantsButNotThirdUser() throws Exception {
        var a = connect(1, "fixture"); var b = connect(2, "fixture"); var c = connect(3, "fixture");
        var aEvents = subscribe(a); var bEvents = subscribe(b); var cEvents = subscribe(c);
        var event = new ChatRealtimeEvent("MESSAGE_CREATED", "50", "60");
        broker.convertAndSendToUser("1", ChatRealtimeEvent.DESTINATION, event);
        broker.convertAndSendToUser("2", ChatRealtimeEvent.DESTINATION, event);
        assertThat(aEvents.poll(5, TimeUnit.SECONDS)).isEqualTo(event);
        assertThat(bEvents.poll(5, TimeUnit.SECONDS)).isEqualTo(event);
        assertThat(cEvents.poll(100, TimeUnit.MILLISECONDS)).isNull();
        var read = new ChatRealtimeEvent("MESSAGE_READ", "50", null);
        broker.convertAndSendToUser("1", ChatRealtimeEvent.DESTINATION, read);
        assertThat(aEvents.poll(5, TimeUnit.SECONDS)).isEqualTo(read);
        a.disconnect(); b.disconnect(); c.disconnect();
    }
    @Test void invalidTokenCannotCompleteStompConnection() {
        assertThatThrownBy(() -> connect(1, "invalid")).isInstanceOf(Exception.class);
    }
    @Test void forgedPrivateSubscriptionReceivesNoOtherUsersEvents() throws Exception {
        var session = connect(1, "fixture");
        var events = new LinkedBlockingQueue<ChatRealtimeEvent>();
        session.subscribe("/user/2/queue/chat-events", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return ChatRealtimeEvent.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { events.add((ChatRealtimeEvent) payload); }
        });
        broker.convertAndSendToUser("2", ChatRealtimeEvent.DESTINATION, new ChatRealtimeEvent("MESSAGE_CREATED", "50", "60"));
        assertThat(events.poll(300, TimeUnit.MILLISECONDS)).isNull();
        if (session.isConnected()) session.disconnect();
    }
}
