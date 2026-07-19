package io.kelta.runtime.context;

import java.util.Optional;

/**
 * Holder for the request-origin geolocation ({@link GeoStamp}), mirroring
 * {@link TenantContext}. Bound per request by the worker's {@code TenantContextFilter}
 * from the gateway's trusted {@code X-Geo-*} headers; unbound on flow, scheduled and
 * system execution paths (those have no HTTP origin).
 */
public final class GeoContext {

    public static final ScopedValue<GeoStamp> CURRENT_GEO = ScopedValue.newInstance();

    private GeoContext() {
    }

    /** The current request's geo stamp, or empty when none is bound. */
    public static Optional<GeoStamp> current() {
        return CURRENT_GEO.isBound() ? Optional.ofNullable(CURRENT_GEO.get()) : Optional.empty();
    }

    /**
     * The current request-origin ISO country, or {@code ""} when unbound — the same
     * empty value the gateway's Cerbos principal uses for unknown origins.
     */
    public static String currentCountry() {
        return current().map(GeoStamp::country).orElse("");
    }
}
