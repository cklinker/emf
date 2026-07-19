package io.kelta.runtime.context;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request-origin geolocation resolved by the gateway and forwarded via the trusted
 * {@code X-Geo-*} headers. {@code country} is always present (ISO-3166 alpha-2);
 * every other field may be null — GeoLite2 region/city coverage is partial.
 */
public record GeoStamp(
        String country,
        String region,
        String city,
        Double latitude,
        Double longitude,
        Integer accuracyKm) {

    /**
     * Map form used when stamping geo onto records (serialized to a JSONB column).
     * Null fields are omitted.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("country", country);
        if (region != null) map.put("region", region);
        if (city != null) map.put("city", city);
        if (latitude != null) map.put("lat", latitude);
        if (longitude != null) map.put("lon", longitude);
        if (accuracyKm != null) map.put("accuracyKm", accuracyKm);
        return map;
    }
}
