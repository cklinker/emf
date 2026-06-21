package io.kelta.worker.health.rule;

import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.health.HealthContext;
import io.kelta.worker.health.HealthFinding;
import io.kelta.worker.health.HealthSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverpermissiveProfileRule")
class OverpermissiveProfileRuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private final OverpermissiveProfileRule rule = new OverpermissiveProfileRule();

    @Test
    @DisplayName("flags a profile granted VIEW_ALL_DATA")
    void flagsProfile() {
        when(jdbcTemplate.queryForList(anyString(), eq("t1"))).thenReturn(List.of(
                Map.of("id", "p1", "name", "Support", "permission_key", "VIEW_ALL_DATA")));

        List<HealthFinding> findings = rule.evaluate(new HealthContext("t1", null, jdbcTemplate));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(HealthSeverity.WARNING);
            assertThat(f.targetType()).isEqualTo(MetadataType.PROFILE);
            assertThat(f.targetId()).isEqualTo("p1");
            assertThat(f.detail()).contains("VIEW_ALL_DATA");
        });
    }

    @Test
    @DisplayName("no findings when no profile is over-permissive")
    void noneWhenEmpty() {
        when(jdbcTemplate.queryForList(anyString(), eq("t1"))).thenReturn(List.of());
        assertThat(rule.evaluate(new HealthContext("t1", null, jdbcTemplate))).isEmpty();
    }
}
