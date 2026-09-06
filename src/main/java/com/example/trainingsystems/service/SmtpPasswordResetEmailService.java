package com.example.trainingsystems.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SmtpPasswordResetEmailService implements PasswordResetEmailService {
    private static final Logger logger =
        LoggerFactory.getLogger(SmtpPasswordResetEmailService.class);

    private final JavaMailSender mailSender;
    private final TaskExecutor mailExecutor;
    private final String from;

    public SmtpPasswordResetEmailService(
        JavaMailSender mailSender,
        @Qualifier("passwordResetMailExecutor") TaskExecutor mailExecutor,
        @Value("${MAIL_FROM:}") String from
    ) {
        this.mailSender = mailSender;
        this.mailExecutor = mailExecutor;
        this.from = from == null ? "" : from.trim();
    }

    @Override
    public void sendResetCode(String email, String code, Duration validFor) {
        if (from.isBlank()) {
            throw new IllegalStateException("Password reset mail is not configured");
        }
        mailExecutor.execute(() -> deliver(email, code, validFor));
    }

    private void deliver(String email, String code, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("RehabAssist 密碼重設驗證碼");
        message.setText(
            "您的密碼重設驗證碼為：" + code + "\n\n" +
            "驗證碼將於 " + validFor.toMinutes() + " 分鐘後失效。" +
            "請勿將驗證碼提供給任何人。\n\n" +
            "若您未提出密碼重設要求，請忽略此信。"
        );
        try {
            mailSender.send(message);
        } catch (RuntimeException error) {
            // Never include the destination or verification code in logs.
            logger.warn("Password reset email delivery failed");
        }
    }
}
