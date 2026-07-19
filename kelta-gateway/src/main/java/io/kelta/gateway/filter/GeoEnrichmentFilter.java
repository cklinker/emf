package io.kelta.gateway.filter;

import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.auth.JwtAuthenticationFilter;
import io.kelta.gateway.geo.ClientIpResolver;
import io.kelta.gateway.geo.GeoIpLookupService;
import io.kelta.gateway.geo.GeoResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Enriches every request with IP-geolocation context: resolves the client IP, looks it
 * up in the local GeoLite2 database, stores the {@link GeoResult} as the
 * {@code "gateway.geo"} exchange attribute, stamps trusted {@code X-Geo-*} headers onto
 * the downstream request, and carries the country onto the {@link GatewayPrincipal} for
 * Cerbos policies.
 *
 * <p>Client-supplied {@code X-Geo-*} headers are stripped by
 * {@link IdentityHeaderStripFilter} (order -400), so downstream services can trust these
 * values. Fail-open: when no database is loaded or the IP is private/unknown, the
 * request proceeds without geo headers and Cerbos sees an empty {@code geoCountry}.
 *
 * <p>Ordered at -45: after {@code UserIdentityResolutionFilter} (-50) so the principal
 * is fully resolved before the geo copy, and before {@code TenantIpAllowlistFilter}
 * (-40) / {@code RouteAuthorizationFilter} (0) so authorization can use the data.
 */
@Component
public class GeoEnrichmentFilter implements GlobalFilter, Ordered {

    /** Exchange attribute holding the {@link GeoResult} for this request. */
    public static final String GEO_ATTRIBUTE = "gateway.geo";

    public static final String HEADER_COUNTRY = "X-Geo-Country";
    public static final String HEADER_REGION = "X-Geo-Region";
    public static final String HEADER_CITY = "X-Geo-City";
    public static final String HEADER_LAT = "X-Geo-Lat";
    public static final String HEADER_LON = "X-Geo-Lon";
    public static final String HEADER_ACCURACY = "X-Geo-Accuracy-Km";

    private static final String PRINCIPAL_ATTRIBUTE = "gateway.principal";

    private final ClientIpResolver clientIpResolver;
    private final GeoIpLookupService lookupService;
    private final boolean enabled;

    public GeoEnrichmentFilter(
            ClientIpResolver clientIpResolver,
            GeoIpLookupService lookupService,
            @Value("${kelta.gateway.geo.enabled:true}") boolean enabled) {
        this.clientIpResolver = clientIpResolver;
        this.lookupService = lookupService;
        this.enabled = enabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        GeoResult geo = lookupService.lookup(clientIpResolver.resolve(exchange)).orElse(null);
        if (geo == null) {
            return chain.filter(exchange);
        }

        exchange.getAttributes().put(GEO_ATTRIBUTE, geo);

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        if (principal != null && geo.country() != null) {
            exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal.withGeoCountry(geo.country()));
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> {
                    setIfPresent(headers::set, HEADER_COUNTRY, geo.country());
                    setIfPresent(headers::set, HEADER_REGION, encode(geo.region()));
                    setIfPresent(headers::set, HEADER_CITY, encode(geo.city()));
                    setIfPresent(headers::set, HEADER_LAT, toPlainString(geo.latitude()));
                    setIfPresent(headers::set, HEADER_LON, toPlainString(geo.longitude()));
                    setIfPresent(headers::set, HEADER_ACCURACY,
                            geo.accuracyKm() != null ? String.valueOf(geo.accuracyKm()) : null);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static void setIfPresent(java.util.function.BiConsumer<String, String> setter,
                                     String name, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(name, value);
        }
    }

    /** Percent-encodes UTF-8 text (city/region names) into a header-safe ASCII value. */
    static String encode(String value) {
        if (value == null) {
            return null;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String toPlainString(Double value) {
        return value != null ? String.format(Locale.ROOT, "%.4f", value) : null;
    }

    @Override
    public int getOrder() {
        return -45; // After UserIdentityResolutionFilter (-50), before TenantIpAllowlistFilter (-40)
    }
}
