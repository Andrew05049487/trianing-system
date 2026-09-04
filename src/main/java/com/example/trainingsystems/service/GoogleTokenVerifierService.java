package com.example.trainingsystems.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Locale;

@Service
public class GoogleTokenVerifierService implements GoogleIdentityVerifier {
    private final String webClientId;
    private final GoogleIdTokenVerifier verifier;

    @Autowired
    public GoogleTokenVerifierService(
        @Value("${google.oauth.web-client-id:}") String webClientId
    ) {
        this(webClientId, buildVerifier(webClientId));
    }

    GoogleTokenVerifierService(String webClientId, GoogleIdTokenVerifier verifier) {
        this.webClientId = webClientId == null ? "" : webClientId.trim();
        this.verifier = verifier;
    }

    @Override
    public VerifiedGoogleIdentity verify(String idToken) {
        if (webClientId.isBlank() || verifier == null) {
            throw new AuthApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "GOOGLE_AUTH_NOT_CONFIGURED",
                "伺服器尚未完成 Google 登入設定"
            );
        }
        if (idToken == null || idToken.isBlank()) {
            throw invalidToken();
        }

        try {
            GoogleIdToken verifiedToken = verifier.verify(idToken);
            if (verifiedToken == null) {
                throw invalidToken();
            }
            return validatedIdentity(verifiedToken.getPayload());
        } catch (GeneralSecurityException | IOException error) {
            throw invalidToken();
        }
    }

    VerifiedGoogleIdentity validatedIdentity(GoogleIdToken.Payload payload) {
        if (payload == null ||
            payload.getSubject() == null || payload.getSubject().isBlank() ||
            payload.getEmail() == null || payload.getEmail().isBlank() ||
            !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw invalidToken();
        }

        String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
        Object nameClaim = payload.get("name");
        String name = nameClaim == null ? "" : nameClaim.toString().trim();
        if (name.isBlank()) {
            int atIndex = email.indexOf('@');
            name = atIndex > 0 ? email.substring(0, atIndex) : "Google 使用者";
        }
        return new VerifiedGoogleIdentity(payload.getSubject(), email, name);
    }

    private static GoogleIdTokenVerifier buildVerifier(String webClientId) {
        if (webClientId == null || webClientId.isBlank()) {
            return null;
        }
        try {
            return new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance()
            ).setAudience(Collections.singletonList(webClientId.trim())).build();
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("無法初始化 Google ID token 驗證器", error);
        }
    }

    private AuthApiException invalidToken() {
        return new AuthApiException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_GOOGLE_TOKEN",
            "Google 身分驗證失敗"
        );
    }
}
