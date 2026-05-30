package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveResult;
import io.kelta.worker.repository.GovernorLimitsRepository;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("User/Flow/Report quota enforcement hooks")
class UserFlowReportQuotaEnforcementHookTest {

    @Mock
    private TenantQuotaResolver resolver;

    @Mock
    private GovernorLimitsRepository repository;

    @Nested
    @DisplayName("UserQuotaEnforcementHook")
    class UserHook {
        @Test
        void allowsBelowLimit() {
            when(resolver.intQuota("t", TenantTierQuotas.KEY_MAX_USERS)).thenReturn(100);
            when(repository.countActiveUsers("t")).thenReturn(99);

            BeforeSaveResult r = new UserQuotaEnforcementHook(resolver, repository)
                    .beforeCreate(Map.of("email", "a@b.c"), "t");

            assertThat(r.isSuccess()).isTrue();
        }

        @Test
        void rejectsAtLimit() {
            when(resolver.intQuota("t", TenantTierQuotas.KEY_MAX_USERS)).thenReturn(5);
            when(repository.countActiveUsers("t")).thenReturn(5);

            BeforeSaveResult r = new UserQuotaEnforcementHook(resolver, repository)
                    .beforeCreate(Map.of("email", "a@b.c"), "t");

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getErrors().get(0).message()).contains("Tenant user quota exceeded (5/5)");
        }

        @Test
        void targetsUsersCollection() {
            assertThat(new UserQuotaEnforcementHook(resolver, repository).getCollectionName())
                    .isEqualTo("users");
        }
    }

    @Nested
    @DisplayName("FlowQuotaEnforcementHook")
    class FlowHook {
        @Test
        void allowsBelowLimit() {
            when(resolver.intQuota("t", TenantTierQuotas.KEY_MAX_WORKFLOWS)).thenReturn(50);
            when(repository.countActiveFlows("t")).thenReturn(49);

            BeforeSaveResult r = new FlowQuotaEnforcementHook(resolver, repository)
                    .beforeCreate(Map.of("name", "flow"), "t");

            assertThat(r.isSuccess()).isTrue();
        }

        @Test
        void rejectsAtLimit() {
            when(resolver.intQuota("t", TenantTierQuotas.KEY_MAX_WORKFLOWS)).thenReturn(5);
            when(repository.countActiveFlows("t")).thenReturn(5);

            BeforeSaveResult r = new FlowQuotaEnforcementHook(resolver, repository)
                    .beforeCreate(Map.of("name", "flow"), "t");

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getErrors().get(0).message()).contains("Tenant workflow quota exceeded (5/5)");
        }

        @Test
        void targetsFlowsCollection() {
            assertThat(new FlowQuotaEnforcementHook(resolver, repository).getCollectionName())
                    .isEqualTo("flows");
        }
    }

    @Nested
    @DisplayName("ReportQuotaEnforcementHook")
    class ReportHook {
        @Test
        void allowsBelowLimit() {
            when(resolver.intQuota("t", TenantTierQuotas.KEY_MAX_REPORTS)).thenReturn(200);
            when(repository.countReports("t")).thenReturn(199);

            BeforeSaveResult r = new ReportQuotaEnforcementHook(resolver, repository)
                    .beforeCreate(Map.of("name", "rep"), "t");

            assertThat(r.isSuccess()).isTrue();
        }

        @Test
        void rejectsAtLimit() {
            when(resolver.intQuota("t", TenantTierQuotas.KEY_MAX_REPORTS)).thenReturn(10);
            when(repository.countReports("t")).thenReturn(10);

            BeforeSaveResult r = new ReportQuotaEnforcementHook(resolver, repository)
                    .beforeCreate(Map.of("name", "rep"), "t");

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.getErrors().get(0).message()).contains("Tenant report quota exceeded (10/10)");
        }

        @Test
        void targetsReportsCollection() {
            assertThat(new ReportQuotaEnforcementHook(resolver, repository).getCollectionName())
                    .isEqualTo("reports");
        }
    }

    @Test
    @DisplayName("all three hooks allow when tenant blank")
    void allowsBlankTenant() {
        BeforeSaveResult u = new UserQuotaEnforcementHook(resolver, repository)
                .beforeCreate(Map.of(), null);
        BeforeSaveResult f = new FlowQuotaEnforcementHook(resolver, repository)
                .beforeCreate(Map.of(), "");
        BeforeSaveResult r = new ReportQuotaEnforcementHook(resolver, repository)
                .beforeCreate(Map.of(), "   ");

        assertThat(u.isSuccess()).isTrue();
        assertThat(f.isSuccess()).isTrue();
        assertThat(r.isSuccess()).isTrue();
    }
}
