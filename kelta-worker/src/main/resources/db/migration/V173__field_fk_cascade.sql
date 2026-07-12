-- Deleting a field 500'd (SQL state 23503) whenever layout or picklist metadata
-- still referenced it: five FKs to field(id) had no ON DELETE action, so Postgres
-- rejected the delete. Field deletion is documented as destructive (the column and
-- its data are dropped), so dependent UI/metadata rows must go with the field —
-- the same semantics profile_field_permission (ON DELETE CASCADE) and
-- collection.display_field_id (ON DELETE SET NULL) already have.

ALTER TABLE layout_field
    DROP CONSTRAINT layout_field_field_id_fkey,
    ADD CONSTRAINT layout_field_field_id_fkey
        FOREIGN KEY (field_id) REFERENCES field(id) ON DELETE CASCADE;

ALTER TABLE layout_related_list
    DROP CONSTRAINT layout_related_list_relationship_field_id_fkey,
    ADD CONSTRAINT layout_related_list_relationship_field_id_fkey
        FOREIGN KEY (relationship_field_id) REFERENCES field(id) ON DELETE CASCADE;

ALTER TABLE picklist_dependency
    DROP CONSTRAINT picklist_dependency_controlling_field_id_fkey,
    ADD CONSTRAINT picklist_dependency_controlling_field_id_fkey
        FOREIGN KEY (controlling_field_id) REFERENCES field(id) ON DELETE CASCADE,
    DROP CONSTRAINT picklist_dependency_dependent_field_id_fkey,
    ADD CONSTRAINT picklist_dependency_dependent_field_id_fkey
        FOREIGN KEY (dependent_field_id) REFERENCES field(id) ON DELETE CASCADE;

ALTER TABLE record_type_picklist
    DROP CONSTRAINT record_type_picklist_field_id_fkey,
    ADD CONSTRAINT record_type_picklist_field_id_fkey
        FOREIGN KEY (field_id) REFERENCES field(id) ON DELETE CASCADE;
