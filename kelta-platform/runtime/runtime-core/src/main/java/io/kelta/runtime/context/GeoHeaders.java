package io.kelta.runtime.context;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Parses the gateway-forwarded {@code X-Geo-*} headers into a {@link GeoStamp}.
 *
 * <p>The gateway strips client-supplied {@code X-Geo-*} headers before setting its own,
 * so worker-side these values are trusted. Parsing is still defensive — a malformed
 * value must never fail the request; the whole stamp (or the bad field) is simply
 * dropped. City/region values are percent-encoded UTF-8 on the wire.
 */
public final class GeoHeaders {

    public static final String HEADER_COUNTRY = "X-Geo-Country";
    public static final String HEADER_REGION = "X-Geo-Region";
    public static final String HEADER_CITY = "X-Geo-City";
    public static final String HEADER_LAT = "X-Geo-Lat";
    public static final String HEADER_LON = "X-Geo-Lon";
    public static final String HEADER_ACCURACY = "X-Geo-Accuracy-Km";

    private GeoHeaders() {
    }

    /**
     * Returns the request's geo stamp, or empty when the gateway attached no geo
     * context (private IP, no database loaded, or geo disabled).
     */
    public static Optional<GeoStamp> parse(HttpServletRequest request) {
        String country = request.getHeader(HEADER_COUNTRY);
        if (country == null || !isIsoCountry(country)) {
            return Optional.empty();
        }
        return Optional.of(new GeoStamp(
                country.toUpperCase(java.util.Locale.ROOT),
                decode(request.getHeader(HEADER_REGION)),
                decode(request.getHeader(HEADER_CITY)),
                parseDouble(request.getHeader(HEADER_LAT)),
                parseDouble(request.getHeader(HEADER_LON)),
                parseInt(request.getHeader(HEADER_ACCURACY))));
    }

    private static boolean isIsoCountry(String value) {
        return value.length() == 2
                && Character.isLetter(value.charAt(0))
                && Character.isLetter(value.charAt(1));
    }

    private static String decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
