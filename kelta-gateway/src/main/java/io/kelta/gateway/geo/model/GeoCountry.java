package io.kelta.gateway.geo.model;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;

/** {@code country} sub-document of a GeoLite2-City record (ISO-3166 alpha-2 code only). */
public record GeoCountry(String isoCode) {

    @MaxMindDbConstructor
    public GeoCountry(@MaxMindDbParameter(name = "iso_code") String isoCode) {
        this.isoCode = isoCode;
    }
}
