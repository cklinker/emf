-- Per-collection opt-in: stamp request-origin geolocation (gateway X-Geo-* headers)
-- into created_geo/updated_geo JSONB system columns on HTTP record writes.
ALTER TABLE collection
    ADD COLUMN capture_geo boolean DEFAULT false NOT NULL;

COMMENT ON COLUMN collection.capture_geo IS
    'When true, HTTP record writes stamp the request-origin geolocation into created_geo/updated_geo';
