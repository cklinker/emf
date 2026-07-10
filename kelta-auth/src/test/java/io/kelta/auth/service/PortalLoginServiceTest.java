package io.kelta.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PortalLoginService")
class PortalLoginServiceTest {

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final String VERIFY_URL = "https://auth.example.com/portal/login/verify";

    private JdbcTemplate jdbcTemplate;
    private WorkerClient workerClient;
    private PortalLoginService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        workerClient = mock(WorkerClient.class);
        service = new PortalLoginService(jdbcTemplate, workerClient);
        when(workerClient.sendTemplateEmail(anyString(), anyString(), anyString(), any(),
                anyString(), anyString())).thenReturn(true);
    }

    private void stubPortalUser() {
        when(jdbcTemplate.queryForList(contains("user_type = 'PORTAL'"),
                eq("pat@example.com"), eq(TENANT)))
                .thenReturn(List.of(Map.of("id", "u1", "tenant_name", "Acme")));
    }

    private void stubRecentTokenCount(int count) {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM portal_login_token"),
                eq(Integer.class), eq(TENANT), eq("u1"), any())).thenReturn(count);
    }

    @Test
    @DisplayName("requestLink stores a hashed token and emails the raw one")
    void requestLinkHappyPath() {
        stubPortalUser();
        stubRecentTokenCount(0);

        service.requestLink(TENANT, "  Pat@Example.com ", VERIFY_URL);

        // The stored value is a 64-char SHA-256 hex hash, not the raw token.
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("INSERT INTO portal_login_token"),
                anyString(), eq(TENANT), eq("u1"), hashCaptor.capture(), any());
        assertThat(hashCaptor.getValue()).hasSize(64);

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(workerClient).sendTemplateEmail(eq(TENANT), eq("pat@example.com"),
                eq("portal.login-link"), vars.capture(), eq("PORTAL_LOGIN"), eq("u1"));
        String actionUrl = (String) vars.getValue().get("actionUrl");
        assertThat(actionUrl).startsWith(VERIFY_URL + "?token=");
        // Raw token in the link hashes to the stored hash (link and row correspond).
        String rawToken = actionUrl.substring((VERIFY_URL + "?token=").length());
        assertThat(PortalLoginService.sha256(rawToken)).isEqualTo(hashCaptor.getValue());
    }

    @Test
    @DisplayName("requestLink is silent for unknown emails — no token, no email")
    void requestLinkUnknownEmail() {
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        service.requestLink(TENANT, "stranger@example.com", VERIFY_URL);

        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any(), any(), any());
        verifyNoInteractions(workerClient);
    }

    @Test
    @DisplayName("requestLink rate-limits repeated requests per user")
    void requestLinkRateLimited() {
        stubPortalUser();
        stubRecentTokenCount(3);

        service.requestLink(TENANT, "pat@example.com", VERIFY_URL);

        verify(jdbcTemplate, never()).update(contains("INSERT INTO portal_login_token"),
                any(), any(), any(), any(), any());
        verifyNoInteractions(workerClient);
    }

    @Test
    @DisplayName("verify consumes the token atomically and loads the portal user")
    void verifyHappyPath() {
        when(jdbcTemplate.queryForList(contains("SET consumed_at"), anyString()))
                .thenReturn(List.of(Map.of("user_id", "u1", "tenant_id", TENANT)));
        when(jdbcTemplate.query(contains("user_type = 'PORTAL'"), any(RowMapper.class),
                eq("u1"), eq(TENANT)))
                .thenAnswer(inv -> List.of(new PortalLoginService.PortalVerification(
                        new io.kelta.auth.model.KeltaUserDetails(
                                "u1", "pat@example.com", TENANT, "profile-portal",
                                "Portal User", "Pat Doe", "", true, false, false, "PORTAL"),
                        "acme")));

        Optional<PortalLoginService.PortalVerification> result = service.verify("raw-token");

        assertThat(result).isPresent();
        assertThat(result.get().userDetails().getUserType()).isEqualTo("PORTAL");
        assertThat(result.get().tenantSlug()).isEqualTo("acme");
        // Consumption keyed on the hash, never the raw token.
        verify(jdbcTemplate).queryForList(contains("token_hash = ?"),
                eq(PortalLoginService.sha256("raw-token")));
    }

    @Test
    @DisplayName("verify rejects unknown, expired, or already-consumed tokens")
    void verifyRejectsInvalidToken() {
        when(jdbcTemplate.queryForList(contains("SET consumed_at"), anyString()))
                .thenReturn(List.of());

        assertThat(service.verify("bad-token")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    @DisplayName("verify rejects tokens whose user is no longer an active portal user")
    void verifyRejectsInactiveUser() {
        when(jdbcTemplate.queryForList(contains("SET consumed_at"), anyString()))
                .thenReturn(List.of(Map.of("user_id", "u1", "tenant_id", TENANT)));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("u1"), eq(TENANT)))
                .thenReturn(List.of());

        assertThat(service.verify("raw-token")).isEmpty();
    }

    @Test
    @DisplayName("resolveTenantUuid passes UUIDs through and resolves slugs")
    void resolveTenant() {
        assertThat(service.resolveTenantUuid(TENANT)).contains(TENANT);

        when(jdbcTemplate.queryForList(contains("FROM tenant WHERE slug"),
                eq(String.class), eq("acme"))).thenReturn(List.of(TENANT));
        assertThat(service.resolveTenantUuid("acme")).contains(TENANT);

        when(jdbcTemplate.queryForList(contains("FROM tenant WHERE slug"),
                eq(String.class), eq("ghost"))).thenReturn(List.of());
        assertThat(service.resolveTenantUuid("ghost")).isEmpty();
        assertThat(service.resolveTenantUuid(null)).isEmpty();
    }
}
