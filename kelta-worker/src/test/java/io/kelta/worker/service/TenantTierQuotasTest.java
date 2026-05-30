package io.kelta.worker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantTierQuotas")
class TenantTierQuotasTest {

    @Test
    @DisplayName("FREE tier defaults are smaller than PROFESSIONAL across every quota")
    void freeIsSmallerThanProfessional() {
        Map<String, Object> free = TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.FREE);
        Map<String, Object> pro = TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.PROFESSIONAL);

        for (String key : new String[]{
                TenantTierQuotas.KEY_API_CALLS_PER_DAY,
                TenantTierQuotas.KEY_STORAGE_GB,
                TenantTierQuotas.KEY_MAX_USERS,
                TenantTierQuotas.KEY_MAX_COLLECTIONS,
                TenantTierQuotas.KEY_MAX_FIELDS_PER_COLLECTION,
                TenantTierQuotas.KEY_MAX_WORKFLOWS,
                TenantTierQuotas.KEY_MAX_REPORTS,
                TenantTierQuotas.KEY_AI_TOKENS_PER_MONTH}) {
            long freeVal = ((Number) free.get(key)).longValue();
            long proVal = ((Number) pro.get(key)).longValue();
            assertThat(freeVal).as("FREE < PROFESSIONAL for %s", key).isLessThan(proVal);
        }
    }

    @Test
    @DisplayName("ENTERPRISE tier is bigger than PROFESSIONAL across every quota")
    void enterpriseIsBiggerThanProfessional() {
        Map<String, Object> pro = TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.PROFESSIONAL);
        Map<String, Object> ent = TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.ENTERPRISE);

        for (String key : new String[]{
                TenantTierQuotas.KEY_API_CALLS_PER_DAY,
                TenantTierQuotas.KEY_STORAGE_GB,
                TenantTierQuotas.KEY_MAX_USERS,
                TenantTierQuotas.KEY_MAX_COLLECTIONS,
                TenantTierQuotas.KEY_MAX_WORKFLOWS,
                TenantTierQuotas.KEY_AI_TOKENS_PER_MONTH}) {
            long proVal = ((Number) pro.get(key)).longValue();
            long entVal = ((Number) ent.get(key)).longValue();
            assertThat(entVal).as("ENTERPRISE > PROFESSIONAL for %s", key).isGreaterThan(proVal);
        }
    }

    @Test
    @DisplayName("AI is disabled for FREE tier")
    void aiDisabledForFree() {
        assertThat(TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.FREE).get("aiEnabled")).isEqualTo(false);
        assertThat(TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.PROFESSIONAL).get("aiEnabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("fromEdition handles null, blank, and unknown values by defaulting to PROFESSIONAL")
    void fromEditionFallback() {
        assertThat(TenantTierQuotas.Tier.fromEdition(null)).isEqualTo(TenantTierQuotas.Tier.PROFESSIONAL);
        assertThat(TenantTierQuotas.Tier.fromEdition("")).isEqualTo(TenantTierQuotas.Tier.PROFESSIONAL);
        assertThat(TenantTierQuotas.Tier.fromEdition("   ")).isEqualTo(TenantTierQuotas.Tier.PROFESSIONAL);
        assertThat(TenantTierQuotas.Tier.fromEdition("bogus")).isEqualTo(TenantTierQuotas.Tier.PROFESSIONAL);
        assertThat(TenantTierQuotas.Tier.fromEdition("free")).isEqualTo(TenantTierQuotas.Tier.FREE);
        assertThat(TenantTierQuotas.Tier.fromEdition("ENTERPRISE")).isEqualTo(TenantTierQuotas.Tier.ENTERPRISE);
    }

    @Test
    @DisplayName("mergeOverrides preserves tier defaults for keys not in override")
    void mergePreservesUntouchedDefaults() {
        Map<String, Object> overrides = Map.of(TenantTierQuotas.KEY_MAX_USERS, 999);

        Map<String, Object> merged = TenantTierQuotas.mergeOverrides(
                TenantTierQuotas.Tier.PROFESSIONAL, overrides);

        assertThat(merged.get(TenantTierQuotas.KEY_MAX_USERS)).isEqualTo(999);
        assertThat(merged.get(TenantTierQuotas.KEY_API_CALLS_PER_DAY)).isEqualTo(100_000);
        assertThat(merged.get(TenantTierQuotas.KEY_AI_ENABLED)).isEqualTo(true);
    }

    @Test
    @DisplayName("mergeOverrides handles null override map")
    void mergeWithNullOverrides() {
        Map<String, Object> merged = TenantTierQuotas.mergeOverrides(TenantTierQuotas.Tier.FREE, null);
        assertThat(merged).isEqualTo(TenantTierQuotas.defaultsFor(TenantTierQuotas.Tier.FREE));
    }
}
