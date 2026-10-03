package com.example.trainingsystems.chat;

import com.example.trainingsystems.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatRealtimeNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(ChatRealtimeNotifier.class);
    private final SimpMessagingTemplate messages;
    private final ChatService chats;

    public ChatRealtimeNotifier(SimpMessagingTemplate messages, ChatService chats) {
        this.messages = messages;
        this.chats = chats;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ChatRealtimeEvent event) {
        try {
            // Fresh transaction: a removed relationship/account cannot receive events.
            for (Long recipient : chats.realtimeRecipients(Long.valueOf(event.conversationId()))) {
                messages.convertAndSendToUser(recipient.toString(), ChatRealtimeEvent.DESTINATION, event);
            }
        } catch (RuntimeException ignored) {
            // A committed REST write must not look failed because a socket is unavailable.
            LOG.warn("Chat realtime delivery unavailable; REST resynchronization required");
        }
    }
}
