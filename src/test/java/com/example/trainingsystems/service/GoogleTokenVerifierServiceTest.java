package com.example.trainingsystems.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleTokenVerifierServiceTest {
    @Test
    void invalidOrWrongAudienceTokenIsRejectedWhenVerifierReturnsNull() throws Exception {
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify("bad-token")).thenReturn(null);
        GoogleTokenVerifierService service = new GoogleTokenVerifierService(
            "web-client-id.apps.googleusercontent.com",
            verifier
        );

        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.verify("bad-token")
        );
        assertEquals("INVALID_GOOGLE_TOKEN", error.getCode());
    }

    @Test
    void unverifiedEmailIsRejected() throws Exception {
        GoogleTokenVerifierService service = serviceReturning(payload(
            "subject", "patient@example.com", false
        ));
        assertThrows(AuthApiException.class, () -> service.verify("token"));
    }

    @Test
    void missingSubjectOrEmailIsRejected() throws Exception {
        assertThrows(
            AuthApiException.class,
            () -> serviceReturning(payload(null, "patient@example.com", true)).verify("token")
        );
        assertThrows(
            AuthApiException.class,
            () -> serviceReturning(payload("subject", null, true)).verify("token")
        );
    }

    @Test
    void verifiedClaimsUseSubjectAndNormalizedEmail() throws Exception {
        GoogleIdToken.Payload payload = payload("stable-sub", "PATIENT@Example.COM", true);
        payload.set("name", " 王小明 ");

        VerifiedGoogleIdentity identity = serviceReturning(payload).verify("token");

        assertEquals("stable-sub", identity.subject());
        assertEquals("patient@example.com", identity.email());
        assertEquals("王小明", identity.name());
    }

    @Test
    void missingServerClientIdFailsClearly() {
        GoogleTokenVerifierService service = new GoogleTokenVerifierService("", null);
        AuthApiException error = assertThrows(
            AuthApiException.class,
            () -> service.verify("token")
        );
        assertEquals("GOOGLE_AUTH_NOT_CONFIGURED", error.getCode());
    }

    private GoogleTokenVerifierService serviceReturning(GoogleIdToken.Payload payload)
        throws Exception {
        GoogleIdToken token = mock(GoogleIdToken.class);
        when(token.getPayload()).thenReturn(payload);
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        when(verifier.verify("token")).thenReturn(token);
        return new GoogleTokenVerifierService(
            "web-client-id.apps.googleusercontent.com",
            verifier
        );
    }

    private GoogleIdToken.Payload payload(String subject, String email, boolean verified) {
        return new GoogleIdToken.Payload()
            .setSubject(subject)
            .setEmail(email)
            .setEmailVerified(verified);
    }
}
