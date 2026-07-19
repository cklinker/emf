package io.kelta.gateway.geo.model;

import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;

/** {@code location} sub-document of a GeoLite2-City record. */
public record GeoLocation(Double latitude, Double longitude, Integer accuracyRadius) {

    @MaxMindDbConstructor
    public GeoLocation(
            @MaxMindDbParameter(name = "latitude") Double latitude,
            @MaxMindDbParameter(name = "longitude") Double longitude,
            @MaxMindDbParameter(name = "accuracy_radius") Integer accuracyRadius) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyRadius = accuracyRadius;
    }
}
