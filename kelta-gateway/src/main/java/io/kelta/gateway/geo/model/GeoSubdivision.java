package io.kelta.gateway.geo.model;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;

import java.util.Map;

/** One {@code subdivisions[]} entry of a GeoLite2-City record (region/state). */
public record GeoSubdivision(String isoCode, Map<String, String> names) {

    @MaxMindDbConstructor
    public GeoSubdivision(
            @MaxMindDbParameter(name = "iso_code") String isoCode,
            @MaxMindDbParameter(name = "names") Map<String, String> names) {
        this.isoCode = isoCode;
        this.names = names;
    }

    /** English display name, falling back to the ISO code. */
    public String displayName() {
        if (names != null && names.get("en") != null) {
            return names.get("en");
        }
        return isoCode;
    }
}
