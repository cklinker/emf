package io.kelta.worker.health;

import io.kelta.worker.dependency.MetadataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigHealthReport")
class ConfigHealthReportTest {

    private static HealthFinding finding(HealthSeverity severity) {
        return HealthFinding.of("R", severity, "t", "d", MetadataType.COLLECTION, "id", "name");
    }

    @Test
    @DisplayName("score is 100 minus summed severity penalties; INFO is free")
    void scoring() {
        ConfigHealthReport report = ConfigHealthReport.from(
                List.of(finding(HealthSeverity.ERROR),      // -20
                        finding(HealthSeverity.WARNING),    // -5
                        finding(HealthSeverity.WARNING),    // -5
                        finding(HealthSeverity.INFO)),      // -0
                6);

        assertThat(report.score()).isEqualTo(70);
        assertThat(report.rulesRun()).isEqualTo(6);
    }

    @Test
    @DisplayName("score floors at 0")
    void scoreFloor() {
        ConfigHealthReport report = ConfigHealthReport.from(
                List.of(finding(HealthSeverity.ERROR), finding(HealthSeverity.ERROR),
                        finding(HealthSeverity.ERROR), finding(HealthSeverity.ERROR),
                        finding(HealthSeverity.ERROR), finding(HealthSeverity.ERROR)),
                6);
        assertThat(report.score()).isZero();
    }

    @Test
    @DisplayName("summary counts findings per severity")
    void summary() {
        ConfigHealthReport report = ConfigHealthReport.from(
                List.of(finding(HealthSeverity.ERROR), finding(HealthSeverity.WARNING),
                        finding(HealthSeverity.INFO), finding(HealthSeverity.INFO)),
                6);
        assertThat(report.summary()).containsEntry("error", 1L)
                .containsEntry("warning", 1L).containsEntry("info", 2L);
        assertThat(report.toMap()).containsEntry("score", 75).containsEntry("findingCount", 4);
    }
}
