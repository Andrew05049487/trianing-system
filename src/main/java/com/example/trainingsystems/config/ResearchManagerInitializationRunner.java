package com.example.trainingsystems.config;

import com.example.trainingsystems.service.ResearchManagerInitializationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Disabled unless a deployment operator explicitly supplies all target settings. */
@Component
public class ResearchManagerInitializationRunner implements ApplicationRunner {
    private final ResearchManagerInitializationService service;
    private final String targetId;
    private final String email;
    private final String reference;

    public ResearchManagerInitializationRunner(ResearchManagerInitializationService service,
        @Value("${RESEARCH_INITIAL_MANAGER_USER_ID:}") String targetId,
        @Value("${RESEARCH_INITIAL_MANAGER_EMAIL:}") String email,
        @Value("${RESEARCH_INITIAL_MANAGER_REFERENCE:}") String reference) {
        this.service = service;
        this.targetId = targetId;
        this.email = email;
        this.reference = reference;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (targetId.isBlank() && email.isBlank() && reference.isBlank()) return;
        try {
            service.initialize(Long.valueOf(targetId), email, reference);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Research initialization target ID invalid");
        }
    }
}
