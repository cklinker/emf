package io.kelta.worker.health;

import java.util.List;

/**
 * A single configuration-health check. Implementations are Spring {@code @Component}s; the
 * {@link ConfigHealthAnalyzer} collects them all and runs each against a {@link HealthContext}.
 *
 * <p>A rule must be side-effect free and resilient: it should never throw for ordinary data, but
 * the analyzer isolates failures so one broken rule never fails the whole scan.
 *
 * @since 1.0.0
 */
public interface HealthRule {

    /** Stable machine key for this rule (e.g. {@code CIRCULAR_MASTER_DETAIL}). */
    String key();

    /** Evaluates the rule, returning zero or more findings. */
    List<HealthFinding> evaluate(HealthContext context);
}
