package com.example.trainingsystems.chat;

import com.example.trainingsystems.service.ChatService;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;

/** Real transaction synchronization on isolated H2; not MySQL/Render E2E. */
@SpringJUnitConfig(ChatRealtimeTransactionTest.Config.class)
class ChatRealtimeTransactionTest {
    @Configuration @EnableTransactionManagement @Import(ChatRealtimeNotifier.class)
    static class Config {
        @Bean DataSource dataSource() { return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build(); }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean ChatService chats() { return mock(ChatService.class); }
        @Bean SimpMessagingTemplate messages() { return mock(SimpMessagingTemplate.class); }
    }
    @Autowired ApplicationEventPublisher events;
    @Autowired DataSourceTransactionManager transactions;
    @Autowired SimpMessagingTemplate messages;
    @Autowired ChatService chats;
    @BeforeEach void setup() { reset(messages, chats); when(chats.realtimeRecipients(50L)).thenReturn(List.of(1L, 2L)); }
    @Test void commitNotifiesBothParticipantsOnlyAfterTransaction() {
        var event = new ChatRealtimeEvent("MESSAGE_CREATED", "50", "60");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event); verifyNoInteractions(messages);
        });
        verify(messages).convertAndSendToUser("1", ChatRealtimeEvent.DESTINATION, event);
        verify(messages).convertAndSendToUser("2", ChatRealtimeEvent.DESTINATION, event);
        verifyNoMoreInteractions(messages);
    }
    @Test void rollbackAndNontransactionalPublishingNeverNotify() {
        var event = new ChatRealtimeEvent("MESSAGE_CREATED", "50", "60");
        events.publishEvent(event);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event); status.setRollbackOnly();
        });
        verifyNoInteractions(messages);
    }
    @Test void readChangeNotifiesBothWithoutMessageText() {
        var event = new ChatRealtimeEvent("MESSAGE_READ", "50", null);
        new TransactionTemplate(transactions).executeWithoutResult(status -> events.publishEvent(event));
        verify(messages).convertAndSendToUser("1", ChatRealtimeEvent.DESTINATION, event);
        verify(messages).convertAndSendToUser("2", ChatRealtimeEvent.DESTINATION, event);
    }
    @Test void removedRelationshipStopsDeliveryAndBrokerFailureDoesNotUndoCommit() {
        when(chats.realtimeRecipients(50L)).thenReturn(List.of());
        new TransactionTemplate(transactions).executeWithoutResult(status -> events.publishEvent(new ChatRealtimeEvent("MESSAGE_READ", "50", null)));
        verifyNoInteractions(messages);
        when(chats.realtimeRecipients(50L)).thenThrow(new IllegalStateException("unavailable"));
        new TransactionTemplate(transactions).executeWithoutResult(status -> events.publishEvent(new ChatRealtimeEvent("MESSAGE_READ", "50", null)));
        verifyNoInteractions(messages);
    }
}
