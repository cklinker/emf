-- V181 gave every FK that referenced collection(id) an ON DELETE action so a used
-- collection could finally be deleted — but it enumerated the 14 FKs known at the time and
-- missed one added later by the record-versioning feature (#1266): record_version.
-- record_version.collection_id references collection(id) with NO ON DELETE action, so a
-- collection with collection-level history enabled (trackHistory=true) accumulates a
-- record_version snapshot row per record write and becomes undeletable again with the same
-- 409 REFERENCED_RECORD — the exact failure V181 set out to fix, for versioned collections.
--
-- record_version is OWNED BY the collection (it is that collection's history), and it has no
-- delete API of its own, so it must cascade on collection delete just like field_history —
-- same reasoning as V181's CASCADE bucket. record_version is a leaf (nothing references
-- record_version(id)), so no intermediate cascade edges are needed.
ALTER TABLE record_version
    DROP CONSTRAINT record_version_collection_id_fkey,
    ADD CONSTRAINT record_version_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;
