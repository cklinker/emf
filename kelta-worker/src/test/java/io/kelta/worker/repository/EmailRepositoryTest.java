package io.kelta.worker.repository;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EmailRepository")
class EmailRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private EmailRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new EmailRepository(jdbcTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("findTemplateByName returns the tenant override when one exists")
    void findTemplateByNamePrefersTenantOverride() {
        Map<String, Object> tenantRow = Map.of(
                "id", "tpl-tenant",
                "subject", "Tenant subject",
                "body_html", "<p>tenant</p>",
                "tenant_id", "t1"
        );
        when(jdbcTemplate.queryForList(contains("FROM email_template"),
                eq("password_reset"), eq("t1"), eq("t1")))
                .thenReturn(List.of(tenantRow));

        var result = repository.findTemplateByName("t1", "password_reset");

        assertThat(result).isPresent();
        assertThat(result.get().get("id")).isEqualTo("tpl-tenant");
        assertThat(result.get().get("tenant_id")).isEqualTo("t1");
    }

    @Test
    @DisplayName("findTemplateByName falls back to the system default when no tenant override")
    void findTemplateByNameFallsBackToSystemDefault() {
        Map<String, Object> systemRow = Map.of(
                "id", "tpl-sys",
                "subject", "Reset your password",
                "body_html", "<p>system</p>",
                "tenant_id", "system"
        );
        when(jdbcTemplate.queryForList(contains("FROM email_template"),
                eq("password_reset"), eq("t1"), eq("t1")))
                .thenReturn(List.of(systemRow));

        var result = repository.findTemplateByName("t1", "password_reset");

        assertThat(result).isPresent();
        assertThat(result.get().get("tenant_id")).isEqualTo("system");
        assertThat(result.get().get("subject")).isEqualTo("Reset your password");
    }

    @Test
    @DisplayName("findTemplateByName returns empty when neither tenant nor system template exists")
    void findTemplateByNameReturnsEmptyWhenMissing() {
        when(jdbcTemplate.queryForList(contains("FROM email_template"),
                anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        var result = repository.findTemplateByName("t1", "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findTemplateByName scopes the lookup to tenant and 'system' only")
    void findTemplateByNameScopesToTenantAndSystem() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.findTemplateByName("t1", "welcome");

        verify(jdbcTemplate).queryForList(
                contains("tenant_id IN (?, 'system')"),
                eq("welcome"), eq("t1"), eq("t1"));
    }
}
