package io.kelta.gateway.geo.model;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;

import java.util.Map;

/** {@code city} sub-document of a GeoLite2-City record. */
public record GeoCity(Map<String, String> names) {

    @MaxMindDbConstructor
    public GeoCity(@MaxMindDbParameter(name = "names") Map<String, String> names) {
        this.names = names;
    }

    /** English display name, or null when the record has no city. */
    public String displayName() {
        return names != null ? names.get("en") : null;
    }
}
