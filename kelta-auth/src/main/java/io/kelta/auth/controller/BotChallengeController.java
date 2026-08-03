package io.kelta.auth.controller;

import io.kelta.auth.service.botchallenge.BotChallengeVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Issues proof-of-work challenges for the public portal endpoints
 * (consumer-alerting slice 7).
 *
 * <p>Unauthenticated by necessity — it is what an anonymous visitor calls before
 * signing up. Handing out challenges is cheap and stateless (no per-challenge
 * server state exists until a solution is submitted), but the path is still
 * per-IP rate limited at the gateway so it cannot be used as a hashing oracle.
 *
 * <p>Returns 404 when the challenge is not enabled, so a frontend can tell
 * "this deployment does not require a challenge" from a misconfiguration.
 */
@RestController
@RequestMapping("/portal/api")
public class BotChallengeController {

    private final BotChallengeVerifier verifier;

    public BotChallengeController(BotChallengeVerifier verifier) {
        this.verifier = verifier;
    }

    @GetMapping("/challenge")
    public ResponseEntity<Map<String, Object>> challenge() {
        if (!verifier.enabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(verifier.issue());
    }
}
