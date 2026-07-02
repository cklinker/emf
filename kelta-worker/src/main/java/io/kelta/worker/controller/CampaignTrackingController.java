package io.kelta.worker.controller;

import io.kelta.worker.repository.CampaignRecipientRepository;
import io.kelta.worker.repository.CampaignRepository;
import io.kelta.worker.repository.EmailSuppressionRepository;
import io.kelta.worker.service.campaign.TrackingTokenService;
import io.kelta.worker.util.TenantContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Public (unauthenticated) tracking endpoints for campaign emails: the open pixel, the
 * click-redirect, and the unsubscribe link.
 *
 * <p>These are exposed via the gateway {@code unauthenticated-paths} allowlist, so the HMAC
 * tracking token is the sole authenticator. Every request verifies the token, resolves the
 * recipient (and thereby the tenant) from it, then records the event under that tenant's RLS
 * context. Forged or tampered tokens are rejected — an attacker cannot poison another tenant's
 * stats or unsubscribe arbitrary addresses.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/track")
public class CampaignTrackingController {

    private static final Logger log = LoggerFactory.getLogger(CampaignTrackingController.class);

    /** A 1x1 transparent GIF. */
    private static final byte[] PIXEL = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final CampaignRecipientRepository recipientRepository;
    private final CampaignRepository campaignRepository;
    private final EmailSuppressionRepository suppressionRepository;
    private final TrackingTokenService tokenService;

    public CampaignTrackingController(CampaignRecipientRepository recipientRepository,
                                      CampaignRepository campaignRepository,
                                      EmailSuppressionRepository suppressionRepository,
                                      TrackingTokenService tokenService) {
        this.recipientRepository = recipientRepository;
        this.campaignRepository = campaignRepository;
        this.suppressionRepository = suppressionRepository;
        this.tokenService = tokenService;
    }

    @GetMapping("/open")
    public ResponseEntity<byte[]> open(@RequestParam("t") String token) {
        recipient(token).ifPresent(r -> withRecipient(r, () -> {
            if (recipientRepository.recordOpen(id(r))) {
                campaignRepository.incrementOpen(campaignId(r));
            }
        }));
        // Always return the pixel regardless of token validity — never leak which tokens are valid.
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_GIF)
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .body(PIXEL);
    }

    @GetMapping("/click")
    public ResponseEntity<Void> click(@RequestParam("t") String token, @RequestParam("u") String url) {
        if (!isSafeUrl(url)) {
            return ResponseEntity.badRequest().build();
        }
        recipient(token).ifPresent(r -> withRecipient(r, () -> {
            if (recipientRepository.recordClick(id(r))) {
                campaignRepository.incrementClick(campaignId(r));
            }
        }));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribeGet(@RequestParam("t") String token) {
        return doUnsubscribe(token);
    }

    /** One-click unsubscribe (RFC 8058 List-Unsubscribe-Post). Idempotent. */
    @PostMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribePost(@RequestParam("t") String token) {
        return doUnsubscribe(token);
    }

    private ResponseEntity<String> doUnsubscribe(String token) {
        Optional<Map<String, Object>> recipient = recipient(token);
        if (recipient.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(page("Invalid link", "This unsubscribe link is invalid or has expired."));
        }
        Map<String, Object> r = recipient.get();
        withRecipient(r, () -> {
            boolean first = recipientRepository.markUnsubscribed(id(r));
            suppressionRepository.add((String) r.get("tenant_id"), (String) r.get("email"),
                    "UNSUBSCRIBE", campaignId(r), null);
            if (first) {
                campaignRepository.incrementUnsubscribe(campaignId(r));
            }
        });
        return ResponseEntity.ok(page("Unsubscribed",
                "You have been unsubscribed and will no longer receive these emails."));
    }

    private Optional<Map<String, Object>> recipient(String token) {
        return tokenService.verify(token).flatMap(recipientRepository::findByIdAnyTenant);
    }

    private void withRecipient(Map<String, Object> r, Runnable action) {
        try {
            TenantContextUtils.withTenant((String) r.get("tenant_id"), action::run);
        } catch (Exception e) {
            log.warn("Failed to record tracking event for recipient {}: {}", id(r), e.getMessage());
        }
    }

    private static String id(Map<String, Object> r) {
        return (String) r.get("id");
    }

    private static String campaignId(Map<String, Object> r) {
        return (String) r.get("campaign_id");
    }

    /** Guards the click redirect against open-redirect to non-http schemes (javascript:, data:, …). */
    private static boolean isSafeUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String page(String title, String message) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title></head>"
                + "<body style=\"font-family:sans-serif;max-width:480px;margin:80px auto;text-align:center\">"
                + "<h1 style=\"font-size:20px\">" + title + "</h1>"
                + "<p style=\"color:#555\">" + message + "</p></body></html>";
    }
}
