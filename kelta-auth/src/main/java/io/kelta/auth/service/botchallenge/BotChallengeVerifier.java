package io.kelta.auth.service.botchallenge;

import java.util.Map;

/**
 * Proof-of-work bot challenge on unauthenticated write endpoints
 * (consumer-alerting slice 7).
 *
 * <p>A rate limit bounds how fast one IP can abuse public signup; it does nothing
 * against a distributed script with a thousand addresses, each staying under the
 * budget. The challenge attaches a small CPU cost to every attempt, which is
 * negligible for a human filling in a form once and prohibitive at scale.
 *
 * <p>This is an interface, not a single class, because the challenge scheme is
 * the part most likely to change — the two implementers of interest are the
 * self-hosted proof-of-work scheme in {@link AltchaBotChallengeVerifier} and
 * whatever a deployment prefers instead. Proprietary hosted CAPTCHAs are
 * deliberately not the default: they send every visitor's IP to a third party and
 * do not fit the project's open-source rule.
 */
public interface BotChallengeVerifier {

    /** Outcome of verifying a submitted solution. */
    enum Result {
        /** Solution verified; the request may proceed. */
        VALID,
        /** Enforcement is off — callers proceed without a challenge. */
        DISABLED,
        /** No solution was submitted. */
        MISSING,
        /** Malformed, unsigned, wrong, expired, or already used. */
        INVALID
    }

    /** Whether enforcement is on. When false, {@link #verify} returns DISABLED. */
    boolean enabled();

    /**
     * Issues a fresh challenge for a client to solve.
     *
     * @return the challenge fields a client widget needs
     * @throws IllegalStateException if called while disabled
     */
    Map<String, Object> issue();

    /**
     * Verifies a submitted solution. Implementations must reject replays — a
     * solution that can be reused is one unit of work for unlimited requests.
     *
     * @param payload the client-submitted solution, or null/blank if none
     */
    Result verify(String payload);
}
