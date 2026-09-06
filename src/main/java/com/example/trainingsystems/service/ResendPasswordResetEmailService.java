package com.example.trainingsystems.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

@Service
public class ResendPasswordResetEmailService
    implements PasswordResetEmailService {

    private static final Logger logger = LoggerFactory.getLogger(
        ResendPasswordResetEmailService.class
    );
    private static final String RESEND_BASE_URL = "https://api.resend.com";
    private static final String SUBJECT = "RehabAssist 密碼重設驗證碼";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final TaskExecutor mailExecutor;
    private final String apiKey;
    private final String from;

    @Autowired
    public ResendPasswordResetEmailService(
        RestClient.Builder restClientBuilder,
        @Qualifier("passwordResetMailExecutor") TaskExecutor mailExecutor,
        @Value("${RESEND_API_KEY:}") String apiKey,
        @Value("${RESEND_FROM:}") String from
    ) {
        this(
            createRestClient(restClientBuilder),
            mailExecutor,
            apiKey,
            from
        );
    }

    ResendPasswordResetEmailService(
        RestClient restClient,
        TaskExecutor mailExecutor,
        String apiKey,
        String from
    ) {
        this.restClient = restClient;
        this.mailExecutor = mailExecutor;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from == null ? "" : from.trim();
    }

    @Override
    public void sendResetCode(String email, String code, Duration validFor) {
        requireConfiguration();
        mailExecutor.execute(() -> deliver(email, code, validFor));
    }

    private void deliver(String email, String code, Duration validFor) {
        ResendEmailRequest request = new ResendEmailRequest(
            from,
            List.of(email),
            SUBJECT,
            messageText(code, validFor)
        );
        try {
            restClient.post()
                .uri(RESEND_BASE_URL + "/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException error) {
            logger.warn(
                "Resend password reset delivery rejected: status={}, type={}",
                error.getStatusCode().value(),
                error.getClass().getSimpleName()
            );
        } catch (RuntimeException error) {
            logger.warn(
                "Resend password reset delivery failed: type={}",
                error.getClass().getSimpleName()
            );
        }
    }

    private void requireConfiguration() {
        if (apiKey.isBlank()) {
            logger.error(
                "Resend password reset delivery is unavailable: "
                    + "RESEND_API_KEY is not configured"
            );
            throw new IllegalStateException(
                "Resend password reset delivery is not configured"
            );
        }
        if (from.isBlank()) {
            logger.error(
                "Resend password reset delivery is unavailable: "
                    + "RESEND_FROM is not configured"
            );
            throw new IllegalStateException(
                "Resend password reset delivery is not configured"
            );
        }
    }

    private static RestClient createRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(HTTP_TIMEOUT);
        requestFactory.setReadTimeout(HTTP_TIMEOUT);
        return builder
            .requestFactory(requestFactory)
            .build();
    }

    private static String messageText(String code, Duration validFor) {
        return "您的密碼重設驗證碼為：" + code + "\n\n"
            + "驗證碼將於 " + validFor.toMinutes() + " 分鐘後失效。\n"
            + "請勿將驗證碼提供給任何人。\n\n"
            + "若您未提出密碼重設要求，請忽略此信。";
    }

    private record ResendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String text
    ) {
    }
}
