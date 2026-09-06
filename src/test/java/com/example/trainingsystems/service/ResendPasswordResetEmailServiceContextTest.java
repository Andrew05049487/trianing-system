package com.example.trainingsystems.service;

import com.example.trainingsystems.config.AuthSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResendPasswordResetEmailServiceContextTest {
    @Test
    void springContextCreatesExactlyOnePasswordResetEmailServiceBean() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                    "resend-test",
                    Map.of(
                        "RESEND_API_KEY",
                        UUID.randomUUID().toString(),
                        "RESEND_FROM",
                        "RehabAssist <sender@example.test>"
                    )
                )
            );
            context.registerBean(RestClient.Builder.class, () -> RestClient.builder());
            context.register(
                AuthSecurityConfiguration.class,
                ResendPasswordResetEmailService.class
            );

            context.refresh();

            assertNotNull(
                context.getBean(ResendPasswordResetEmailService.class)
            );
            assertEquals(
                1,
                context.getBeansOfType(PasswordResetEmailService.class).size()
            );
        }
    }
}
