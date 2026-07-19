-- IP geolocation for login history: populated by the worker's LoginTrackingFilter
-- from the gateway's trusted X-Geo-* headers. Null when the request origin could
-- not be geolocated (private IP, no GeoLite2 database loaded, or geo disabled).
ALTER TABLE login_history
    ADD COLUMN geo_country VARCHAR(2),
    ADD COLUMN geo_region  VARCHAR(100),
    ADD COLUMN geo_city    VARCHAR(150),
    ADD COLUMN geo_lat     DOUBLE PRECISION,
    ADD COLUMN geo_lon     DOUBLE PRECISION;

COMMENT ON COLUMN login_history.geo_country IS 'ISO-3166 alpha-2 country of the login source IP (gateway GeoLite2 lookup)';
