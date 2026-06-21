package io.kelta.worker.health;

import io.kelta.worker.dependency.MetadataDependencyGraph;
import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.service.MetadataDependencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigHealthAnalyzer")
class ConfigHealthAnalyzerTest {

    @Mock
    private MetadataDependencyService dependencyService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private static HealthRule rule(String key, HealthFinding... findings) {
        return new HealthRule() {
            public String key() {
                return key;
            }

            public List<HealthFinding> evaluate(HealthContext context) {
                return List.of(findings);
            }
        };
    }

    private static HealthRule throwingRule() {
        return new HealthRule() {
            public String key() {
                return "BOOM";
            }

            public List<HealthFinding> evaluate(HealthContext context) {
                throw new RuntimeException("boom");
            }
        };
    }

    private static HealthFinding finding(HealthSeverity severity) {
        return HealthFinding.of("R", severity, "t", "d", MetadataType.COLLECTION, "id", "name");
    }

    @Test
    @DisplayName("runs all rules, isolates failures, aggregates findings, and scores")
    void analyze() {
        when(dependencyService.buildGraph("t1")).thenReturn(new MetadataDependencyGraph());

        ConfigHealthAnalyzer analyzer = new ConfigHealthAnalyzer(dependencyService, jdbcTemplate,
                List.of(rule("A", finding(HealthSeverity.WARNING)),
                        throwingRule(),
                        rule("B", finding(HealthSeverity.ERROR))));

        ConfigHealthReport report = analyzer.analyze("t1");

        // The throwing rule is isolated; the other two contribute.
        assertThat(report.findings()).hasSize(2);
        assertThat(report.rulesRun()).isEqualTo(3);
        assertThat(report.score()).isEqualTo(75); // 100 - 20 (ERROR) - 5 (WARNING)
        // Most severe first.
        assertThat(report.findings().get(0).severity()).isEqualTo(HealthSeverity.ERROR);
    }
}
