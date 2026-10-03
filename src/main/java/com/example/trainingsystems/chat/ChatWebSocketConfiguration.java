package com.example.trainingsystems.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.http.MediaType;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final ChatStompInterceptor identity;
    private final ObjectProvider<SimpMessagingTemplate> messages;
    public ChatWebSocketConfiguration(ChatStompInterceptor identity, ObjectProvider<SimpMessagingTemplate> messages) {
        this.identity = identity; this.messages = messages;
    }

    @Bean
    public ThreadPoolTaskScheduler chatHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-heartbeat-");
        return scheduler;
    }

    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(new StompSubProtocolErrorHandler() {
            @Override public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> input, Throwable error) {
                var headers = StompHeaderAccessor.create(StompCommand.ERROR);
                headers.setMessage("Chat connection denied");
                return MessageBuilder.createMessage("Chat connection denied".getBytes(StandardCharsets.UTF_8), headers.getMessageHeaders());
            }
        });
        // Same-origin browser policy; native Flutter does not require wildcard origins.
        registry.addEndpoint("/ws/chat");
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] {25000, 25000})
                .setTaskScheduler(chatHeartbeatScheduler());
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(identity, new ExecutorChannelInterceptor() {
            @Override public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception error) {
                var h = StompHeaderAccessor.wrap(message);
                // User destinations forward to the synchronous broker channel:
                // signal readiness only once the private subscription is registered.
                if (error == null && handler instanceof UserDestinationMessageHandler
                        && h.getCommand() == StompCommand.SUBSCRIBE && h.getUser() != null
                        && ChatRealtimeEvent.SUBSCRIPTION.equals(h.getDestination())) {
                    var reply = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                    reply.setSessionId(h.getSessionId());
                    reply.setContentType(MediaType.APPLICATION_JSON);
                    reply.setLeaveMutable(true);
                    messages.getObject().convertAndSendToUser(h.getUser().getName(), ChatRealtimeEvent.DESTINATION,
                        new ChatRealtimeEvent("CONNECTION_READY", null, null), reply.getMessageHeaders());
                }
            }
        });
        registration.taskExecutor().corePoolSize(1).maxPoolSize(2).queueCapacity(256);
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(1).maxPoolSize(2).queueCapacity(256);
    }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192).setSendBufferSizeLimit(32768)
                .setSendTimeLimit(10000).setTimeToFirstMessage(15000);
    }
}
