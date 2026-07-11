package io.kelta.worker.controller;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.router.UserIdResolver;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.GovernorLimitsRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.S3StorageService;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import io.kelta.worker.service.telehealth.ArchiveService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TelehealthArchiveController")
class TelehealthArchiveControllerTest {

    private static final String TENANT = "t1";

    private ArchiveService archiveService;
    private JdbcTemplate jdbcTemplate;
    private S3StorageService storageService;
    private UserIdResolver userIdResolver;
    private CerbosPermissionResolver permissionResolver;
    private BootstrapRepository bootstrapRepository;
    private TenantQuotaResolver quotaResolver;
    private GovernorLimitsRepository governorLimitsRepository;
    private ObjectMapper objectMapper;
    private TelehealthArchiveController controller;

    @BeforeEach
    void setUp() {
        archiveService = mock(ArchiveService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        storageService = mock(S3StorageService.class);
        userIdResolver = mock(UserIdResolver.class);
        permissionResolver = mock(CerbosPermissionResolver.class);
        bootstrapRepository = mock(BootstrapRepository.class);
        quotaResolver = mock(TenantQuotaResolver.class);
        governorLimitsRepository = mock(GovernorLimitsRepository.class);
        objectMapper = new ObjectMapper();
        controller = new TelehealthArchiveController(archiveService, jdbcTemplate, storageService,
                userIdResolver, permissionResolver, bootstrapRepository, quotaResolver,
                governorLimitsRepository, objectMapper);
        when(userIdResolver.resolve(anyString(), eq(TENANT))).thenAnswer(inv -> inv.getArgument(0));
    }

    private HttpServletRequest staff() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn("u-staff");
        when(request.getHeader("X-User-Type")).thenReturn("INTERNAL");
        return request;
    }

    private HttpServletRequest portal(String userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(userId);
        when(request.getHeader("X-User-Type")).thenReturn("PORTAL");
        return request;
    }

    private void grantManageData(HttpServletRequest request, boolean granted) {
        when(permissionResolver.getProfileId(request)).thenReturn("profile-1");
        Map<String, Object> perm = new LinkedHashMap<>();
        perm.put("permission_name", "MANAGE_DATA");
        perm.put("granted", granted);
        when(bootstrapRepository.findProfileSystemPermissions("profile-1")).thenReturn(List.of(perm));
    }

    // ------------------------------------------------------------- MANAGE_DATA gates

    @Test
    @DisplayName("legal-hold requires MANAGE_DATA — 200 with it, 403 without")
    void legalHoldPermissionGate() {
        TenantContext.runWithTenant(TENANT, () -> {
            HttpServletRequest granted = staff();
            grantManageData(granted, true);
            when(jdbcTemplate.update(contains("SET legal_hold"), eq(true), eq("arch-1"), eq(TENANT)))
                    .thenReturn(1);
            ResponseEntity<Map<String, Object>> ok = controller.legalHold(granted, "arch-1",
                    new TelehealthArchiveController.LegalHoldRequest(true));
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(ok.getBody()).containsEntry("legalHold", true);

            HttpServletRequest denied = staff();
            grantManageData(denied, false);
            assertStatus(() -> controller.legalHold(denied, "arch-1",
                    new TelehealthArchiveController.LegalHoldRequest(true)), HttpStatus.FORBIDDEN);
        });
    }

    @Test
    @DisplayName("PUT retention-settings requires MANAGE_DATA — 403 without, persists with")
    void retentionSettingsPermissionGate() {
        TenantContext.runWithTenant(TENANT, () -> {
            Map<String, Object> quotas = new LinkedHashMap<>();
            quotas.put(TenantTierQuotas.KEY_ARCHIVE_AFTER_DAYS, 30);
            quotas.put(TenantTierQuotas.KEY_RETENTION_YEARS, 7);
            quotas.put(TenantTierQuotas.KEY_PURGE_LIVE_AFTER_DAYS, 90);
            when(quotaResolver.resolve(TENANT)).thenReturn(quotas);
            when(governorLimitsRepository.findTenantLimits(TENANT))
                    .thenReturn(java.util.Optional.empty());

            HttpServletRequest denied = staff();
            grantManageData(denied, false);
            assertStatus(() -> controller.updateRetentionSettings(denied,
                            new TelehealthArchiveController.RetentionSettingsRequest(60, 10, 120)),
                    HttpStatus.FORBIDDEN);
            verify(governorLimitsRepository, never()).updateTenantLimits(anyString(), anyString());

            HttpServletRequest granted = staff();
            grantManageData(granted, true);
            ResponseEntity<Map<String, Object>> ok = controller.updateRetentionSettings(granted,
                    new TelehealthArchiveController.RetentionSettingsRequest(60, 10, 120));
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(governorLimitsRepository).updateTenantLimits(eq(TENANT), contains("retentionYears"));
        });
    }

    @Test
    @DisplayName("retention-settings rejects out-of-range values (400) and never persists")
    void retentionSettingsValidation() {
        TenantContext.runWithTenant(TENANT, () -> {
            when(governorLimitsRepository.findTenantLimits(TENANT))
                    .thenReturn(java.util.Optional.empty());
            HttpServletRequest granted = staff();
            grantManageData(granted, true);
            assertStatus(() -> controller.updateRetentionSettings(granted,
                            new TelehealthArchiveController.RetentionSettingsRequest(30, 0, 90)),
                    HttpStatus.BAD_REQUEST); // retentionYears must be >= 1
            verify(governorLimitsRepository, never()).updateTenantLimits(anyString(), anyString());
        });
    }

    // ------------------------------------------------------------- Portal own-only

    @Test
    @DisplayName("a portal user is denied detail on an archive that is not theirs")
    void portalDetailOwnOnly() {
        TenantContext.runWithTenant(TENANT, () -> {
            Map<String, Object> archive = new LinkedHashMap<>();
            archive.put("id", "arch-2");
            archive.put("source_type", "CONVERSATION");
            archive.put("portal_user_id", "someone-else");
            when(jdbcTemplate.queryForList(contains("FROM telehealth_archive"), eq("arch-2"), eq(TENANT)))
                    .thenReturn(List.of(archive));

            assertStatus(() -> controller.get(portal("u-portal"), "arch-2"), HttpStatus.FORBIDDEN);
        });
    }

    @Test
    @DisplayName("a portal user's archive list is forced to their own portal_user_id")
    void portalListForcedToOwn() {
        TenantContext.runWithTenant(TENANT, () -> {
            when(jdbcTemplate.query(contains("FROM telehealth_archive"),
                    any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                    .thenReturn(List.of());

            controller.list(portal("u-portal"), null, "attacker-supplied-id", null, null);

            org.mockito.ArgumentCaptor<Object[]> args =
                    org.mockito.ArgumentCaptor.forClass(Object[].class);
            verify(jdbcTemplate).query(contains("AND portal_user_id = ?"),
                    any(org.springframework.jdbc.core.RowMapper.class), args.capture());
            // The bound param is the portal user's own id, not the attacker-supplied one.
            assertThat(args.getValue()).containsExactly(TENANT, "u-portal");
        });
    }

    @Test
    @DisplayName("portal users cannot manual-archive (403)")
    void portalCannotArchive() {
        TenantContext.runWithTenant(TENANT, () ->
                assertStatus(() -> controller.create(portal("u-portal"),
                        new TelehealthArchiveController.ArchiveRequest("CONVERSATION", "conv-1")),
                        HttpStatus.FORBIDDEN));
        verify(archiveService, never()).archiveConversation(anyString(), any(), anyString());
    }

    private void assertStatus(Runnable call, HttpStatus expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(expected));
    }
}
