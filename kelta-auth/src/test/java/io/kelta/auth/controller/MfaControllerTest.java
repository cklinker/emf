package io.kelta.auth.controller;

import io.kelta.auth.service.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static io.kelta.auth.controller.MfaController.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MfaController")
class MfaControllerTest {

    @Mock
    private TotpService totpService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    private MfaController controller;

    @BeforeEach
    void setUp() {
        controller = new MfaController(totpService);
        lenient().when(request.getSession(false)).thenReturn(session);
    }

    @Nested
    @DisplayName("showMfaChallenge")
    class ShowMfaChallenge {

        @Test
        @DisplayName("redirects to login when no MFA pending")
        void redirectsWhenNoMfaPending() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(null);

            String result = controller.showMfaChallenge(session, new ConcurrentModel());

            assertThat(result).isEqualTo("redirect:/login");
        }

        @Test
        @DisplayName("shows challenge page when MFA is pending")
        void showsChallengeWhenPending() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_EMAIL)).thenReturn("user@test.com");

            Model model = new ConcurrentModel();
            String result = controller.showMfaChallenge(session, model);

            assertThat(result).isEqualTo("mfa-challenge");
            assertThat(model.getAttribute("email")).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("redirects to login when TOTP service is null")
        void redirectsWhenTotpServiceNull() {
            MfaController controllerNoTotp = new MfaController(null);

            String result = controllerNoTotp.showMfaChallenge(session, new ConcurrentModel());

            assertThat(result).isEqualTo("redirect:/login");
        }
    }

    @Nested
    @DisplayName("verifyMfaCode")
    class VerifyMfaCode {

        @Test
        @DisplayName("redirects to login when no session")
        void redirectsWhenNoSession() {
            when(request.getSession(false)).thenReturn(null);

            String result = controller.verifyMfaCode("123456", request, new RedirectAttributesModelMap());

            assertThat(result).isEqualTo("redirect:/login");
        }

        @Test
        @DisplayName("redirects to challenge when account is locked")
        void redirectsWhenLocked() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            when(totpService.isMfaLocked("user-1")).thenReturn(true);

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String result = controller.verifyMfaCode("123456", request, attrs);

            assertThat(result).isEqualTo("redirect:/mfa-challenge");
            assertThat(attrs.getFlashAttributes().get("error").toString()).contains("Too many failed");
        }

        @Test
        @DisplayName("completes authentication on valid code")
        void completesOnValidCode() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            when(session.getAttribute(SESSION_MFA_TENANT_ID)).thenReturn("tenant-1");
            when(totpService.isMfaLocked("user-1")).thenReturn(false);
            when(totpService.verifyCodeWithReplayPrevention("user-1", "123456")).thenReturn(true);

            String result = controller.verifyMfaCode("123456", request, new RedirectAttributesModelMap());

            assertThat(result).isEqualTo("redirect:/");
            verify(totpService).resetMfaFailedAttempts("user-1");
            verify(session).removeAttribute(SESSION_MFA_PENDING);
        }

        @Test
        @DisplayName("increments failures on invalid code")
        void incrementsFailuresOnInvalid() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            when(session.getAttribute(SESSION_MFA_TENANT_ID)).thenReturn("tenant-1");
            when(totpService.isMfaLocked("user-1")).thenReturn(false);
            when(totpService.verifyCodeWithReplayPrevention("user-1", "000000")).thenReturn(false);

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String result = controller.verifyMfaCode("000000", request, attrs);

            assertThat(result).isEqualTo("redirect:/mfa-challenge");
            verify(totpService).incrementMfaFailedAttempts("user-1");
        }
    }

    @Nested
    @DisplayName("verifyRecoveryCode")
    class VerifyRecoveryCode {

        @Test
        @DisplayName("completes authentication on valid recovery code")
        void completesOnValidRecovery() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            when(session.getAttribute(SESSION_MFA_TENANT_ID)).thenReturn("tenant-1");
            when(totpService.isMfaLocked("user-1")).thenReturn(false);
            when(totpService.verifyRecoveryCode("user-1", "recovery-123")).thenReturn(true);
            when(totpService.getRemainingRecoveryCodeCount("user-1")).thenReturn(4);

            String result = controller.verifyRecoveryCode("recovery-123", request, new RedirectAttributesModelMap());

            assertThat(result).isEqualTo("redirect:/");
        }

        @Test
        @DisplayName("increments failures on invalid recovery code")
        void incrementsFailuresOnInvalidRecovery() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            when(session.getAttribute(SESSION_MFA_TENANT_ID)).thenReturn("tenant-1");
            when(totpService.isMfaLocked("user-1")).thenReturn(false);
            when(totpService.verifyRecoveryCode("user-1", "bad")).thenReturn(false);

            String result = controller.verifyRecoveryCode("bad", request, new RedirectAttributesModelMap());

            assertThat(result).isEqualTo("redirect:/mfa-challenge");
            verify(totpService).incrementMfaFailedAttempts("user-1");
        }
    }

    @Nested
    @DisplayName("showMfaSetup")
    class ShowMfaSetup {

        @Test
        @DisplayName("generates secret on first visit")
        void generatesSecretOnFirstVisit() {
            when(session.getAttribute(SESSION_MFA_SETUP_REQUIRED)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_EMAIL)).thenReturn("user@test.com");
            when(session.getAttribute(SESSION_MFA_SETUP_SECRET)).thenReturn(null);
            when(totpService.generateSecret()).thenReturn("ABCDEFG");
            when(totpService.getQrCodeUri("user@test.com", "ABCDEFG")).thenReturn("otpauth://totp/...");

            Model model = new ConcurrentModel();
            String result = controller.showMfaSetup(session, model);

            assertThat(result).isEqualTo("mfa-setup");
            verify(session).setAttribute(SESSION_MFA_SETUP_SECRET, "ABCDEFG");
            assertThat(model.getAttribute("qrCodeUri")).isEqualTo("otpauth://totp/...");
        }

        @Test
        @DisplayName("reuses existing secret")
        void reusesExistingSecret() {
            when(session.getAttribute(SESSION_MFA_SETUP_REQUIRED)).thenReturn(true);
            when(session.getAttribute(SESSION_MFA_EMAIL)).thenReturn("user@test.com");
            when(session.getAttribute(SESSION_MFA_SETUP_SECRET)).thenReturn("EXISTING");
            when(totpService.getQrCodeUri("user@test.com", "EXISTING")).thenReturn("otpauth://totp/...");

            controller.showMfaSetup(session, new ConcurrentModel());

            verify(totpService, never()).generateSecret();
        }

        @Test
        @DisplayName("redirects to login when no setup required and no MFA pending")
        void redirectsWhenNotRequired() {
            when(session.getAttribute(SESSION_MFA_SETUP_REQUIRED)).thenReturn(null);
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(null);

            String result = controller.showMfaSetup(session, new ConcurrentModel());

            assertThat(result).isEqualTo("redirect:/login");
        }
    }

    @Nested
    @DisplayName("completeMfaSetup")
    class CompleteMfaSetup {

        @Test
        @DisplayName("enrolls user and shows recovery codes")
        void enrollsUser() {
            when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            when(session.getAttribute(SESSION_MFA_TENANT_ID)).thenReturn("tenant-1");
            when(session.getAttribute(SESSION_MFA_EMAIL)).thenReturn("user@test.com");
            when(session.getAttribute(SESSION_MFA_SETUP_SECRET)).thenReturn("SECRET");
            when(totpService.enrollUser("user-1", "SECRET", "123456"))
                    .thenReturn(List.of("code1", "code2"));

            Model model = new ConcurrentModel();
            String result = controller.completeMfaSetup("123456", request, model, new RedirectAttributesModelMap());

            assertThat(result).isEqualTo("mfa-setup");
            assertThat(model.getAttribute("enrolled")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            List<String> codes = (List<String>) model.getAttribute("recoveryCodes");
            assertThat(codes).containsExactly("code1", "code2");
        }

        @Test
        @DisplayName("redirects on invalid code")
        void redirectsOnInvalidCode() {
            lenient().when(session.getAttribute(SESSION_MFA_USER_ID)).thenReturn("user-1");
            lenient().when(session.getAttribute(SESSION_MFA_TENANT_ID)).thenReturn("tenant-1");
            lenient().when(session.getAttribute(SESSION_MFA_EMAIL)).thenReturn("user@test.com");
            lenient().when(session.getAttribute(SESSION_MFA_SETUP_SECRET)).thenReturn("SECRET");
            when(totpService.enrollUser("user-1", "SECRET", "000000"))
                    .thenThrow(new IllegalArgumentException("Invalid code"));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String result = controller.completeMfaSetup("000000", request, new ConcurrentModel(), attrs);

            assertThat(result).isEqualTo("redirect:/mfa-setup");
        }

        @Test
        @DisplayName("redirects when no session")
        void redirectsWhenNoSession() {
            when(request.getSession(false)).thenReturn(null);

            String result = controller.completeMfaSetup("123456", request, new ConcurrentModel(), new RedirectAttributesModelMap());

            assertThat(result).isEqualTo("redirect:/login");
        }
    }

    @Nested
    @DisplayName("acknowledgeCodes")
    class AcknowledgeCodes {

        @Test
        @DisplayName("completes MFA auth if pending")
        void completesMfaIfPending() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(true);

            String result = controller.acknowledgeCodes(request);

            assertThat(result).isEqualTo("redirect:/");
            verify(session).removeAttribute(SESSION_MFA_PENDING);
        }

        @Test
        @DisplayName("redirects home when not pending")
        void redirectsHomeWhenNotPending() {
            when(session.getAttribute(SESSION_MFA_PENDING)).thenReturn(null);

            String result = controller.acknowledgeCodes(request);

            assertThat(result).isEqualTo("redirect:/");
        }
    }
}
