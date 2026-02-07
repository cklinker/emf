package com.emf.controlplane.validation;

import com.emf.controlplane.exception.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GeolocationFieldValidator implements FieldTypeValidator {

    @Override
    public String getFieldType() {
        return "GEOLOCATION";
    }

    @Override
    public void validateConfig(JsonNode config) {
        // Optional: format (DECIMAL_DEGREES by default)
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validateValue(Object value, JsonNode config) {
        if (value == null) return;
        if (!(value instanceof Map)) {
            throw new ValidationException("value", "GEOLOCATION value must be an object with latitude and longitude");
        }
        Map<String, Object> geo = (Map<String, Object>) value;
        if (!geo.containsKey("latitude") || !geo.containsKey("longitude")) {
            throw new ValidationException("value", "GEOLOCATION requires 'latitude' and 'longitude'");
        }
        double lat = ((Number) geo.get("latitude")).doubleValue();
        double lng = ((Number) geo.get("longitude")).doubleValue();
        if (lat < -90 || lat > 90) {
            throw new ValidationException("value.latitude", "Latitude must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new ValidationException("value.longitude", "Longitude must be between -180 and 180");
        }
    }
}
