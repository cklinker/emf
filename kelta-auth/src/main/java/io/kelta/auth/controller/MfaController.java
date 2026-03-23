package io.kelta.auth.controller;

import io.kelta.auth.service.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controller for MFA challenge and setup pages.
 *
 * <p>Handles the MFA verification step after primary authentication,
 * recovery code flow, and initial MFA enrollment.
 *
 * @since 1.0.0
 */
@Controller
public class MfaController {

    private static final Logger log = LoggerFactory.getLogger(MfaController.class);
    private static final Logger securityLog = LoggerFactory.getLogger("security.audit");

    public static final String SESSION_MFA_USER_ID = "MFA_USER_ID";
    public static final String SESSION_MFA_TENANT_ID = "MFA_TENANT_ID";
    public static final String SESSION_MFA_EMAIL = "MFA_EMAIL";
    public static final String SESSION_MFA_PENDING = "MFA_PENDING";
    public static final String SESSION_MFA_SETUP_REQUIRED = "MFA_SETUP_REQUIRED";
    public static final String SESSION_MFA_SETUP_SECRET = "MFA_SETUP_SECRET";

    private final TotpService totpService;

    public MfaController(TotpService totpService) {
        this.totpService = totpService;
    }

    // -----------------------------------------------------------------------
    // MFA Challenge
    // -----------------------------------------------------------------------

    @GetMapping("/mfa-challenge")
    public String showMfaChallenge(HttpSession session, Model model) {
        if (session.getAttribute(SESSION_MFA_PENDING) == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", session.getAttribute(SESSION_MFA_EMAIL));
        return "mfa-challenge";
    }

    @PostMapping("/mfa-challenge")
    public String verifyMfaCode(@RequestParam String code, HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_MFA_PENDING) == null) {
            return "redirect:/login";
        }

        String userId = (String) session.getAttribute(SESSION_MFA_USER_ID);
        String tenantId = (String) session.getAttribute(SESSION_MFA_TENANT_ID);

        if (totpService.isMfaLocked(userId)) {
            redirectAttributes.addFlashAttribute("error", "Too many failed attempts. Please try again later.");
            return "redirect:/mfa-challenge";
        }

        if (totpService.verifyCodeWithReplayPrevention(userId, code)) {
            totpService.resetMfaFailedAttempts(userId);
            completeMfaAuthentication(session, request);
            securityLog.info("security_event=MFA_CHALLENGE_SUCCESS actor={} tenant={}", userId, tenantId);
            return "redirect:/";
        }

        totpService.incrementMfaFailedAttempts(userId);
        securityLog.warn("security_event=MFA_CHALLENGE_FAILED actor={} tenant={} detail=invalid_totp_code", userId, tenantId);
        redirectAttributes.addFlashAttribute("error", "Invalid verification code. Please try again.");
        return "redirect:/mfa-challenge";
    }

    @PostMapping("/mfa-challenge/recovery")
    public String verifyRecoveryCode(@RequestParam String code, HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_MFA_PENDING) == null) {
            return "redirect:/login";
        }

        String userId = (String) session.getAttribute(SESSION_MFA_USER_ID);
        String tenantId = (String) session.getAttribute(SESSION_MFA_TENANT_ID);

        if (totpService.isMfaLocked(userId)) {
            redirectAttributes.addFlashAttribute("error", "Too many failed attempts. Please try again later.");
            return "redirect:/mfa-challenge";
        }

        if (totpService.verifyRecoveryCode(userId, code)) {
            int remaining = totpService.getRemainingRecoveryCodeCount(userId);
            completeMfaAuthentication(session, request);
            securityLog.info("security_event=RECOVERY_CODE_USED actor={} tenant={} remaining={}", userId, tenantId, remaining);
            return "redirect:/";
        }

        totpService.incrementMfaFailedAttempts(userId);
        securityLog.warn("security_event=MFA_CHALLENGE_FAILED actor={} tenant={} detail=invalid_recovery_code", userId, tenantId);
        redirectAttributes.addFlashAttribute("error", "Invalid recovery code.");
        return "redirect:/mfa-challenge";
    }

    // -----------------------------------------------------------------------
    // MFA Setup
    // -----------------------------------------------------------------------

    @GetMapping("/mfa-setup")
    public String showMfaSetup(HttpSession session, Model model) {
        if (session.getAttribute(SESSION_MFA_SETUP_REQUIRED) == null
                && session.getAttribute(SESSION_MFA_PENDING) == null) {
            return "redirect:/login";
        }

        String email = (String) session.getAttribute(SESSION_MFA_EMAIL);
        String secret = (String) session.getAttribute(SESSION_MFA_SETUP_SECRET);

        if (secret == null) {
            secret = totpService.generateSecret();
            session.setAttribute(SESSION_MFA_SETUP_SECRET, secret);
        }

        String qrCodeUri = totpService.getQrCodeUri(email, secret);
        model.addAttribute("email", email);
        model.addAttribute("qrCodeUri", qrCodeUri);
        model.addAttribute("secret", secret);
        return "mfa-setup";
    }

    @PostMapping("/mfa-setup")
    public String completeMfaSetup(@RequestParam String code, HttpServletRequest request,
                                    Model model, RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        if (session == null) return "redirect:/login";

        String userId = (String) session.getAttribute(SESSION_MFA_USER_ID);
        String tenantId = (String) session.getAttribute(SESSION_MFA_TENANT_ID);
        String email = (String) session.getAttribute(SESSION_MFA_EMAIL);
        String secret = (String) session.getAttribute(SESSION_MFA_SETUP_SECRET);

        if (userId == null || secret == null) return "redirect:/login";

        try {
            List<String> recoveryCodes = totpService.enrollUser(userId, secret, code);
            securityLog.info("security_event=MFA_ENROLLED actor={} tenant={}", userId, tenantId);

            session.removeAttribute(SESSION_MFA_SETUP_SECRET);
            session.removeAttribute(SESSION_MFA_SETUP_REQUIRED);

            model.addAttribute("recoveryCodes", recoveryCodes);
            model.addAttribute("email", email);
            model.addAttribute("enrolled", true);
            return "mfa-setup";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid verification code. Please try again.");
            return "redirect:/mfa-setup";
        }
    }

    @PostMapping("/mfa-setup/complete")
    public String acknowledgeCodes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return "redirect:/login";

        if (session.getAttribute(SESSION_MFA_PENDING) != null) {
            completeMfaAuthentication(session, request);
        }
        return "redirect:/";
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void completeMfaAuthentication(HttpSession session, HttpServletRequest request) {
        session.removeAttribute(SESSION_MFA_PENDING);
        session.removeAttribute(SESSION_MFA_SETUP_REQUIRED);
        session.removeAttribute(SESSION_MFA_USER_ID);
        session.removeAttribute(SESSION_MFA_TENANT_ID);
        session.removeAttribute(SESSION_MFA_EMAIL);
        session.removeAttribute(SESSION_MFA_SETUP_SECRET);

        // Regenerate session ID to prevent session fixation
        request.changeSessionId();
        log.debug("MFA authentication completed, session regenerated");
    }
}
