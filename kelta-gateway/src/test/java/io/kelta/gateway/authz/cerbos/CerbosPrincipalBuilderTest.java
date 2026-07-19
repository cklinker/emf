package io.kelta.gateway.authz.cerbos;

import io.kelta.gateway.TestFixtures;
import io.kelta.gateway.auth.GatewayPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CerbosPrincipalBuilder Tests")
class CerbosPrincipalBuilderTest {

    @Test
    @DisplayName("carries geoCountry onto the Cerbos principal")
    void carriesGeoCountry() {
        GatewayPrincipal principal = TestFixtures.principal().withGeoCountry("PT");

        var proto = CerbosPrincipalBuilder.build(principal).toPrincipal();

        assertThat(proto.getAttrMap()).containsKey("geoCountry");
        assertThat(proto.getAttrMap().get("geoCountry").getStringValue()).isEqualTo("PT");
    }

    @Test
    @DisplayName("renders an empty geoCountry when the origin has no geolocation")
    void emptyGeoCountryWhenAbsent() {
        GatewayPrincipal principal = TestFixtures.principal();

        var proto = CerbosPrincipalBuilder.build(principal).toPrincipal();

        // Policies must be able to distinguish "no geo" ("") — the attribute is always present.
        assertThat(proto.getAttrMap()).containsKey("geoCountry");
        assertThat(proto.getAttrMap().get("geoCountry").getStringValue()).isEmpty();
    }

    @Test
    @DisplayName("decision cache keys include the request-origin country")
    void cacheKeysCarryGeo() {
        GatewayPrincipal pt = TestFixtures.principal().withGeoCountry("PT");
        GatewayPrincipal cn = TestFixtures.principal().withGeoCountry("CN");
        GatewayPrincipal none = TestFixtures.principal();

        assertThat(CerbosAuthorizationService.systemCacheKey(pt, "MANAGE_USERS"))
                .isNotEqualTo(CerbosAuthorizationService.systemCacheKey(cn, "MANAGE_USERS"))
                .endsWith(":geo:PT");
        assertThat(CerbosAuthorizationService.systemCacheKey(none, "MANAGE_USERS"))
                .endsWith(":geo:");
        assertThat(CerbosAuthorizationService.objectCacheKey(pt, "col-1", "read"))
                .isNotEqualTo(CerbosAuthorizationService.objectCacheKey(cn, "col-1", "read"));
        // Tenant prefix must stay first — evictForTenant() prefix-matches on it.
        assertThat(CerbosAuthorizationService.objectCacheKey(pt, "col-1", "read"))
                .startsWith(pt.getTenantId() + ":");
    }
}
