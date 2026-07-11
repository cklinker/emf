package io.kelta.worker.service;

import io.kelta.runtime.module.integration.spi.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PortalUserService")
class PortalUserServiceTest {

    private static final String TENANT = "t1";

    private JdbcTemplate jdbcTemplate;
    private EmailService emailService;
    private TenantQuotaResolver quotaResolver;
    private PortalUserService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        emailService = mock(EmailService.class);
        quotaResolver = mock(TenantQuotaResolver.class);
        service = new PortalUserService(jdbcTemplate, emailService, quotaResolver,
                "https://auth.example.com");
        when(quotaResolver.intQuota(TENANT, TenantTierQuotas.KEY_MAX_PORTAL_USERS))
                .thenReturn(25);
        when(emailService.sendByKey(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenReturn(Optional.of("log-1"));
    }

    private void stubNoExistingUser() {
        when(jdbcTemplate.queryForList(contains("SELECT id, user_type"), eq(TENANT), anyString()))
                .thenReturn(List.of());
    }

    private void stubPortalCount(int count) {
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM platform_user"),
                eq(Integer.class), eq(TENANT))).thenReturn(count);
    }

    private void stubPortalProfile() {
        when(jdbcTemplate.queryForList(contains("name = 'Portal User'"),
                eq(String.class), eq(TENANT))).thenReturn(List.of("profile-portal"));
    }

    private void stubTenantName() {
        when(jdbcTemplate.queryForList(contains("SELECT name FROM tenant"),
                eq(String.class), eq(TENANT))).thenReturn(List.of("Acme"));
    }

    @Test
    @DisplayName("creates a PORTAL user with the Portal User profile and emails a 7-day invite link")
    void createsPortalUser() {
        stubNoExistingUser();
        stubPortalCount(0);
        stubPortalProfile();
        stubTenantName();

        var result = service.invitePortalUser(TENANT, "admin-1", "Pat@Example.com", "Pat", "Doe");

        assertThat(result.created()).isTrue();

        // User insert forces ACTIVE + PORTAL + the portal profile, email lowercased.
        ArgumentCaptor<Object[]> insertArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO platform_user"),
                anyString(), eq(TENANT), eq("pat@example.com"), eq("pat@example.com"),
                eq("Pat"), eq("Doe"), eq("profile-portal"));

        // Token insert is the hashed PORTAL_INVITE row.
        verify(jdbcTemplate).update(contains("INSERT INTO portal_login_token"),
                anyString(), eq(TENANT), eq(result.userId()), anyString(), any());

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendByKey(eq(TENANT), eq("pat@example.com"), eq("portal.invite"),
                vars.capture(), eq("PORTAL_INVITE"), eq(result.userId()));
        assertThat((String) vars.getValue().get("actionUrl"))
                .startsWith("https://auth.example.com/portal/login/verify?token=");
        assertThat(vars.getValue()).containsEntry("tenantName", "Acme");
    }

    @Test
    @DisplayName("never writes a user_credential row — portal users are passwordless")
    void noCredentialRow() {
        stubNoExistingUser();
        stubPortalCount(0);
        stubPortalProfile();
        stubTenantName();

        service.invitePortalUser(TENANT, "admin-1", "pat@example.com", null, null);

        verify(jdbcTemplate, never()).update(contains("user_credential"), any(Object[].class));
    }

    @Test
    @DisplayName("re-inviting an existing PORTAL user issues a fresh link without creating a user")
    void reinvitesExistingPortalUser() {
        when(jdbcTemplate.queryForList(contains("SELECT id, user_type"), eq(TENANT), anyString()))
                .thenReturn(List.of(Map.of("id", "u-existing", "user_type", "PORTAL")));
        stubTenantName();

        var result = service.invitePortalUser(TENANT, "admin-1", "pat@example.com", "Pat", null);

        assertThat(result.created()).isFalse();
        assertThat(result.userId()).isEqualTo("u-existing");
        verify(jdbcTemplate, never()).update(contains("INSERT INTO platform_user"),
                any(), any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate).update(contains("INSERT INTO portal_login_token"),
                anyString(), eq(TENANT), eq("u-existing"), anyString(), any());
        verify(emailService).sendByKey(eq(TENANT), eq("pat@example.com"), eq("portal.invite"),
                any(), eq("PORTAL_INVITE"), eq("u-existing"));
    }

    @Test
    @DisplayName("409 when the email belongs to an INTERNAL user")
    void conflictsWithInternalUser() {
        when(jdbcTemplate.queryForList(contains("SELECT id, user_type"), eq(TENANT), anyString()))
                .thenReturn(List.of(Map.of("id", "u-staff", "user_type", "INTERNAL")));

        assertThatThrownBy(() ->
                service.invitePortalUser(TENANT, "admin-1", "staff@example.com", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(emailService, never()).sendByKey(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("429 when the maxPortalUsers governor is exhausted")
    void governorCapRejects() {
        stubNoExistingUser();
        stubPortalCount(25);

        assertThatThrownBy(() ->
                service.invitePortalUser(TENANT, "admin-1", "pat@example.com", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(jdbcTemplate, never()).update(contains("INSERT INTO platform_user"),
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("fails loudly when the Portal User profile is missing")
    void missingProfileFails() {
        stubNoExistingUser();
        stubPortalCount(0);
        when(jdbcTemplate.queryForList(contains("name = 'Portal User'"),
                eq(String.class), eq(TENANT))).thenReturn(List.of());

        assertThatThrownBy(() ->
                service.invitePortalUser(TENANT, "admin-1", "pat@example.com", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
