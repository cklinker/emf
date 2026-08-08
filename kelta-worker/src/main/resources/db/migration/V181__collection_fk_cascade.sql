-- Collections became permanently undeletable once used. Deleting a collection is a
-- generic record delete against the `collection` table (DynamicCollectionRouter →
-- QueryEngine.delete); PhysicalTableStorageAdapter maps Postgres FK violation 23503 to
-- a 409 REFERENCED_RECORD. 14 FKs referenced collection(id) with NO ON DELETE action,
-- so any dependent row (a list view, a validation rule, and — for tables with no delete
-- API such as field_history / file_attachment / bulk_job / script_trigger — rows written
-- automatically) restricted the delete forever.
--
-- Deleting a collection is inherently destructive (its whole data table and every record
-- go away), so metadata and data OWNED BY the collection must go with it. This mirrors the
-- field(id) cascade precedent in V173 (a field's dependent UI/metadata rows cascade on
-- field delete). Two exceptions where the dependent is a reusable/standalone artifact only
-- loosely associated with the collection get ON DELETE SET NULL instead of CASCADE:
--   * email_template.related_collection_id — a template outlives the collection it was
--     tagged against (nullable; the seeded system templates already carry NULL here).
--   * field.reference_collection_id — a lookup field in collection B pointing at collection
--     A. Cascading would silently delete B's field; instead the target is nulled so the
--     field survives (dangling). This FK ALREADY had ON DELETE SET NULL in the baseline —
--     left as-is, documented here for completeness.
--
-- Note (out of scope, tracked in concerns.md): collection delete still does NOT DROP the
-- physical user-data table, nor purge S3 objects behind cascaded file_attachment rows.

-- ── Direct collection(id) references → CASCADE (owned by the collection) ──────────────
ALTER TABLE approval_process
    DROP CONSTRAINT approval_process_collection_id_fkey,
    ADD CONSTRAINT approval_process_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE bulk_job
    DROP CONSTRAINT bulk_job_collection_id_fkey,
    ADD CONSTRAINT bulk_job_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE field_history
    DROP CONSTRAINT field_history_collection_id_fkey,
    ADD CONSTRAINT field_history_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE file_attachment
    DROP CONSTRAINT file_attachment_collection_id_fkey,
    ADD CONSTRAINT file_attachment_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE layout_assignment
    DROP CONSTRAINT layout_assignment_collection_id_fkey,
    ADD CONSTRAINT layout_assignment_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE layout_related_list
    DROP CONSTRAINT layout_related_list_related_collection_id_fkey,
    ADD CONSTRAINT layout_related_list_related_collection_id_fkey
        FOREIGN KEY (related_collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE list_view
    DROP CONSTRAINT list_view_collection_id_fkey,
    ADD CONSTRAINT list_view_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE note
    DROP CONSTRAINT note_collection_id_fkey,
    ADD CONSTRAINT note_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE page_layout
    DROP CONSTRAINT page_layout_collection_id_fkey,
    ADD CONSTRAINT page_layout_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE record_type
    DROP CONSTRAINT record_type_collection_id_fkey,
    ADD CONSTRAINT record_type_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE report
    DROP CONSTRAINT report_primary_collection_id_fkey,
    ADD CONSTRAINT report_primary_collection_id_fkey
        FOREIGN KEY (primary_collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE script_trigger
    DROP CONSTRAINT script_trigger_collection_id_fkey,
    ADD CONSTRAINT script_trigger_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

ALTER TABLE validation_rule
    DROP CONSTRAINT validation_rule_collection_id_fkey,
    ADD CONSTRAINT validation_rule_collection_id_fkey
        FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE;

-- ── Direct collection(id) reference → SET NULL (reusable, nullable) ───────────────────
ALTER TABLE email_template
    DROP CONSTRAINT email_template_related_collection_id_fkey,
    ADD CONSTRAINT email_template_related_collection_id_fkey
        FOREIGN KEY (related_collection_id) REFERENCES collection(id) ON DELETE SET NULL;

-- ── Intermediate children on the cascade path → CASCADE ──────────────────────────────
-- These do NOT reference collection(id) directly, but a collection delete cascades into
-- approval_process / report / page_layout / record_type, and those cascades would in turn
-- hit these RESTRICTing children (trigger firing order within one DELETE is not
-- guaranteed, so relying on the sibling collection-path delete is unsafe). Give every edge
-- on the tree an ON DELETE action so the recursive cascade completes.
ALTER TABLE approval_instance
    DROP CONSTRAINT approval_instance_approval_process_id_fkey,
    ADD CONSTRAINT approval_instance_approval_process_id_fkey
        FOREIGN KEY (approval_process_id) REFERENCES approval_process(id) ON DELETE CASCADE;

ALTER TABLE dashboard_component
    DROP CONSTRAINT dashboard_component_report_id_fkey,
    ADD CONSTRAINT dashboard_component_report_id_fkey
        FOREIGN KEY (report_id) REFERENCES report(id) ON DELETE CASCADE;

ALTER TABLE layout_assignment
    DROP CONSTRAINT layout_assignment_layout_id_fkey,
    ADD CONSTRAINT layout_assignment_layout_id_fkey
        FOREIGN KEY (layout_id) REFERENCES page_layout(id) ON DELETE CASCADE;

ALTER TABLE layout_assignment
    DROP CONSTRAINT layout_assignment_record_type_id_fkey,
    ADD CONSTRAINT layout_assignment_record_type_id_fkey
        FOREIGN KEY (record_type_id) REFERENCES record_type(id) ON DELETE CASCADE;
