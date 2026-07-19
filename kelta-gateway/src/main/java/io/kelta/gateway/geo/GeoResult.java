package io.kelta.gateway.geo;

/**
 * The result of an IP geolocation lookup. Any field may be null — GeoLite2 city/region
 * coverage is partial. A result with a null {@code country} is never produced; callers
 * treat "no result" as {@code Optional.empty()} instead.
 *
 * @param country     ISO-3166 alpha-2 country code (e.g. "PT")
 * @param region      English region/state name (e.g. "Lisbon")
 * @param city        English city name (e.g. "Cascais")
 * @param latitude    approximate latitude of the IP block
 * @param longitude   approximate longitude of the IP block
 * @param accuracyKm  MaxMind accuracy radius in kilometres
 */
public record GeoResult(
        String country,
        String region,
        String city,
        Double latitude,
        Double longitude,
        Integer accuracyKm) {
}
