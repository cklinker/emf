package io.kelta.gateway.geo.model;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;

import java.util.List;

/**
 * Top-level lookup model for a GeoLite2-City record. Maps only the keys the
 * platform uses; everything else in the MMDB entry is ignored by the reader.
 *
 * <p>These model classes are instantiated reflectively by {@code com.maxmind.db.Reader}
 * — every class here MUST have a matching entry in the gateway's
 * {@code reflect-config.json} or lookups fail on the native image only.
 */
public record GeoCityData(
        GeoCountry country,
        List<GeoSubdivision> subdivisions,
        GeoCity city,
        GeoLocation location) {

    @MaxMindDbConstructor
    public GeoCityData(
            @MaxMindDbParameter(name = "country") GeoCountry country,
            @MaxMindDbParameter(name = "subdivisions") List<GeoSubdivision> subdivisions,
            @MaxMindDbParameter(name = "city") GeoCity city,
            @MaxMindDbParameter(name = "location") GeoLocation location) {
        this.country = country;
        this.subdivisions = subdivisions;
        this.city = city;
        this.location = location;
    }
}
