package io.kelta.worker.health;

/**
 * Severity of a configuration-health finding, with the score penalty it carries.
 *
 * <p>INFO findings are advisory and do not reduce the health score; WARNING and ERROR do.
 *
 * @since 1.0.0
 */
public enum HealthSeverity {
    ERROR(20),
    WARNING(5),
    INFO(0);

    private final int penalty;

    HealthSeverity(int penalty) {
        this.penalty = penalty;
    }

    /** Points subtracted from the per-tenant health score for one finding of this severity. */
    public int penalty() {
        return penalty;
    }
}
