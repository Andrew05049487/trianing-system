package com.example.trainingsystems.chat;

/** Invalidation only: no text, token or patient details leave the REST contract. */
public record ChatRealtimeEvent(String type, String conversationId, String messageId) {
    public static final String DESTINATION = "/queue/chat-events";
    public static final String SUBSCRIPTION = "/user" + DESTINATION;
}
