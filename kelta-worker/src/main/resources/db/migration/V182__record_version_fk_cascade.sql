-- V181 made collections deletable again by giving the 14 direct FKs on collection(id)
-- an ON DELETE action, plus the intermediate edges on the cascade tree. It missed one:
-- record_version.
--
-- record_version is the newest of these tables (V174, collection-level record versioning)
-- and shipped with a bare FK to collection(id) — one migration AFTER V173 fixed exactly
-- this class for field(id). RecordVersionHook writes a full-record snapshot on every
-- create/update/delete once collection.track_history is on, so for any versioned
-- collection the very first write still restricts the delete forever, with the same
-- 23503 → 409 REFERENCED_RECORD symptom V181 set out to remove. A collection with
-- versioning enabled is therefore still undeletable on main today.
--
-- Snapshots are meaningless without the collection they snapshot, so CASCADE matches the
-- field_history semantics V181 chose for the same kind of automatically-written history.
--
-- This is the third occurrence of the class (V173 → field(id), V181 → collection(id),
-- this one). CollectionDeletionGuardSchemaTest now fails the build on any FK to
-- collection(id) left with no ON DELETE action, so a fourth cannot ship silently.

ALTER TABLE record_version
    DROP CONSTRAINT record_version_collection_id_fkey,
    ADD CONSTRAINT record_version_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;
