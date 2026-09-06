package com.example.trainingsystems.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendPasswordResetEmailServiceTest {
    private static final String DESTINATION = "recipient@example.test";
    private static final String CODE = "654321";
    private static final String FROM = "RehabAssist <sender@example.test>";

    private MockRestServiceServer server;
    private String apiKey;
    private ResendPasswordResetEmailService service;
    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        apiKey = UUID.randomUUID().toString();
        service = new ResendPasswordResetEmailService(
            builder.build(),
            new SyncTaskExecutor(),
            apiKey,
            FROM
        );
        serviceLogger = (Logger) LoggerFactory.getLogger(
            ResendPasswordResetEmailService.class
        );
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void contractSendsExpectedResetEmailThroughResendHttps() {
        server.expect(once(), requestTo("https://api.resend.com/emails"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "from": "RehabAssist <sender@example.test>",
                  "to": ["recipient@example.test"],
                  "subject": "RehabAssist 密碼重設驗證碼",
                  "text": "您的密碼重設驗證碼為：654321\\n\\n驗證碼將於 10 分鐘後失效。\\n請勿將驗證碼提供給任何人。\\n\\n若您未提出密碼重設要求，請忽略此信。"
                }
                """))
            .andRespond(withSuccess(
                "{\"id\":\"email-id\"}",
                MediaType.APPLICATION_JSON
            ));

        PasswordResetEmailService contract = service;
        assertDoesNotThrow(() -> contract.sendResetCode(
            DESTINATION,
            CODE,
            Duration.ofMinutes(10)
        ));

        server.verify();
        assertEquals(0, logAppender.list.size());
    }

    @Test
    void providerFailureIsContainedAndLogsOnlySafeMetadata() {
        String providerBody = "{\"message\":\"" + DESTINATION + " "
            + CODE + " " + apiKey + "\"}";
        server.expect(once(), requestTo("https://api.resend.com/emails"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(providerBody));

        assertDoesNotThrow(() -> service.sendResetCode(
            DESTINATION,
            CODE,
            Duration.ofMinutes(10)
        ));

        server.verify();
        String logs = logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
        assertFalse(logs.contains(DESTINATION));
        assertFalse(logs.contains(CODE));
        assertFalse(logs.contains(apiKey));
        assertFalse(logs.contains(providerBody));
    }

    @Test
    void missingApiKeyFailsBeforeSchedulingDelivery() {
        ResendPasswordResetEmailService missing =
            new ResendPasswordResetEmailService(
                RestClient.create(),
                new SyncTaskExecutor(),
                "",
                FROM
            );

        assertThrows(
            IllegalStateException.class,
            () -> missing.sendResetCode(
                DESTINATION,
                CODE,
                Duration.ofMinutes(10)
            )
        );
    }

    @Test
    void missingFromFailsBeforeSchedulingDelivery() {
        ResendPasswordResetEmailService missing =
            new ResendPasswordResetEmailService(
                RestClient.create(),
                new SyncTaskExecutor(),
                apiKey,
                ""
            );

        assertThrows(
            IllegalStateException.class,
            () -> missing.sendResetCode(
                DESTINATION,
                CODE,
                Duration.ofMinutes(10)
            )
        );
    }
}
