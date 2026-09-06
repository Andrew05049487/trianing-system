package com.example.trainingsystems.controller;

import com.example.trainingsystems.dto.AuthLoginResponse;
import com.example.trainingsystems.dto.AuthMessageResponse;
import com.example.trainingsystems.dto.PasswordForgotRequest;
import com.example.trainingsystems.dto.PasswordResetRequest;
import com.example.trainingsystems.dto.TherapistRegisterRequest;
import com.example.trainingsystems.service.AuthService;
import com.example.trainingsystems.service.PasswordResetService;
import com.example.trainingsystems.service.TherapistRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRecoveryControllerTest {
    private final AuthService authService = mock(AuthService.class);
    private final PasswordResetService passwordResetService =
        mock(PasswordResetService.class);
    private final TherapistRegistrationService therapistRegistrationService =
        mock(TherapistRegistrationService.class);
    private final AuthController controller = new AuthController(
        authService,
        passwordResetService,
        therapistRegistrationService
    );

    @Test
    void forgotPasswordAlwaysReturnsGenericMessage() {
        ResponseEntity<AuthMessageResponse> existing = controller.forgotPassword(
            new PasswordForgotRequest("person@example.com")
        );
        ResponseEntity<AuthMessageResponse> missing = controller.forgotPassword(
            new PasswordForgotRequest("missing-account")
        );

        assertEquals(200, existing.getStatusCode().value());
        assertEquals(existing.getBody(), missing.getBody());
        assertEquals(
            PasswordResetService.GENERIC_FORGOT_MESSAGE,
            existing.getBody().message()
        );
    }

    @Test
    void resetPasswordReturnsOnlySuccessMessage() {
        PasswordResetRequest request = new PasswordResetRequest(
            "person@example.com",
            "123456",
            "new-password"
        );

        ResponseEntity<AuthMessageResponse> response =
            controller.resetPassword(request);

        verify(passwordResetService).resetPassword(
            "person@example.com",
            "123456",
            "new-password"
        );
        assertEquals("密碼已更新，請使用新密碼登入", response.getBody().message());
    }

    @Test
    void googleLinkAcceptsCurrentFlutterPasswordField() {
        controller.linkGoogle(Map.of(
            "idToken",
            "google-token",
            "currentPassword",
            "original-password"
        ));

        verify(authService).linkGoogle("google-token", "original-password");
    }

    @Test
    void therapistRegistrationDelegatesWithoutAcceptingRole() {
        TherapistRegisterRequest request = new TherapistRegisterRequest(
            "治療師",
            "therapist@example.com",
            "password"
        );
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        AuthLoginResponse expected = new AuthLoginResponse(
            "註冊成功",
            9L,
            "治療師",
            "therapist@example.com",
            "therapist01",
            "THERAPIST",
            null,
            null,
            "hmac-token",
            false
        );
        when(therapistRegistrationService.register(request, "127.0.0.1"))
            .thenReturn(expected);

        ResponseEntity<AuthLoginResponse> response =
            controller.registerTherapist(request, httpRequest);

        assertEquals(expected, response.getBody());
        assertEquals("THERAPIST", response.getBody().role());
    }
}
