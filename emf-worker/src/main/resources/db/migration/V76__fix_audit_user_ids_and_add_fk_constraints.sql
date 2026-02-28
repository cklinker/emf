-- V76: Fix audit user IDs (email → UUID) and add FK constraints
--
-- The gateway forwards the JWT subject (email) as X-User-Id, which was stored
-- directly in created_by / updated_by columns.  System tables have (or should
-- have) FK constraints to platform_user(id), so these columns must contain
-- UUIDs, not emails.
--
-- This migration:
--   1. Converts email values to platform_user UUIDs where a match exists
--   2. Drops existing inline FK constraints on created_by (from V22-V29)
--   3. Adds named FK constraints on created_by and updated_by for all
--      tenant-scoped tables that store user references

-- =========================================================================
-- Step 1: Update email values to platform_user UUIDs
-- =========================================================================
-- Many child tables do not have their own tenant_id column, so we match
-- on email alone.  The mapping uses DISTINCT ON to handle any edge cases
-- where the same email might exist across tenants (picks first match).

-- Helper: create a temp mapping table (email → user_id)
CREATE TEMPORARY TABLE _user_email_map AS
SELECT DISTINCT ON (pu.email) pu.id AS user_id, pu.email
FROM platform_user pu
ORDER BY pu.email, pu.created_at;

CREATE INDEX _idx_uem ON _user_email_map(email);

-- Macro: update a table's created_by and updated_by columns
-- Each UPDATE joins to the temp mapping table on email

-- Core entity tables
UPDATE collection       SET created_by = m.user_id FROM _user_email_map m WHERE collection.created_by = m.email;
UPDATE collection       SET updated_by = m.user_id FROM _user_email_map m WHERE collection.updated_by = m.email;

UPDATE field            SET created_by = m.user_id FROM _user_email_map m WHERE field.created_by = m.email;
UPDATE field            SET updated_by = m.user_id FROM _user_email_map m WHERE field.updated_by = m.email;

UPDATE collection_version SET created_by = m.user_id FROM _user_email_map m WHERE collection_version.created_by = m.email;
UPDATE collection_version SET updated_by = m.user_id FROM _user_email_map m WHERE collection_version.updated_by = m.email;

UPDATE field_version    SET created_by = m.user_id FROM _user_email_map m WHERE field_version.created_by = m.email;
UPDATE field_version    SET updated_by = m.user_id FROM _user_email_map m WHERE field_version.updated_by = m.email;

-- Security & permissions
UPDATE profile          SET created_by = m.user_id FROM _user_email_map m WHERE profile.created_by = m.email;
UPDATE profile          SET updated_by = m.user_id FROM _user_email_map m WHERE profile.updated_by = m.email;

UPDATE permission_set   SET created_by = m.user_id FROM _user_email_map m WHERE permission_set.created_by = m.email;
UPDATE permission_set   SET updated_by = m.user_id FROM _user_email_map m WHERE permission_set.updated_by = m.email;

UPDATE permset_system_permission SET created_by = m.user_id FROM _user_email_map m WHERE permset_system_permission.created_by = m.email;
UPDATE permset_system_permission SET updated_by = m.user_id FROM _user_email_map m WHERE permset_system_permission.updated_by = m.email;

UPDATE permset_object_permission SET created_by = m.user_id FROM _user_email_map m WHERE permset_object_permission.created_by = m.email;
UPDATE permset_object_permission SET updated_by = m.user_id FROM _user_email_map m WHERE permset_object_permission.updated_by = m.email;

UPDATE permset_field_permission SET created_by = m.user_id FROM _user_email_map m WHERE permset_field_permission.created_by = m.email;
UPDATE permset_field_permission SET updated_by = m.user_id FROM _user_email_map m WHERE permset_field_permission.updated_by = m.email;

UPDATE profile_system_permission SET created_by = m.user_id FROM _user_email_map m WHERE profile_system_permission.created_by = m.email;
UPDATE profile_system_permission SET updated_by = m.user_id FROM _user_email_map m WHERE profile_system_permission.updated_by = m.email;

UPDATE profile_object_permission SET created_by = m.user_id FROM _user_email_map m WHERE profile_object_permission.created_by = m.email;
UPDATE profile_object_permission SET updated_by = m.user_id FROM _user_email_map m WHERE profile_object_permission.updated_by = m.email;

UPDATE profile_field_permission SET created_by = m.user_id FROM _user_email_map m WHERE profile_field_permission.created_by = m.email;
UPDATE profile_field_permission SET updated_by = m.user_id FROM _user_email_map m WHERE profile_field_permission.updated_by = m.email;

UPDATE user_group       SET created_by = m.user_id FROM _user_email_map m WHERE user_group.created_by = m.email;
UPDATE user_group       SET updated_by = m.user_id FROM _user_email_map m WHERE user_group.updated_by = m.email;

UPDATE group_membership SET created_by = m.user_id FROM _user_email_map m WHERE group_membership.created_by = m.email;
UPDATE group_membership SET updated_by = m.user_id FROM _user_email_map m WHERE group_membership.updated_by = m.email;

UPDATE user_permission_set SET created_by = m.user_id FROM _user_email_map m WHERE user_permission_set.created_by = m.email;
UPDATE user_permission_set SET updated_by = m.user_id FROM _user_email_map m WHERE user_permission_set.updated_by = m.email;

UPDATE group_permission_set SET created_by = m.user_id FROM _user_email_map m WHERE group_permission_set.created_by = m.email;
UPDATE group_permission_set SET updated_by = m.user_id FROM _user_email_map m WHERE group_permission_set.updated_by = m.email;

-- UI & layouts
UPDATE ui_page          SET created_by = m.user_id FROM _user_email_map m WHERE ui_page.created_by = m.email;
UPDATE ui_page          SET updated_by = m.user_id FROM _user_email_map m WHERE ui_page.updated_by = m.email;

UPDATE ui_menu          SET created_by = m.user_id FROM _user_email_map m WHERE ui_menu.created_by = m.email;
UPDATE ui_menu          SET updated_by = m.user_id FROM _user_email_map m WHERE ui_menu.updated_by = m.email;

UPDATE ui_menu_item     SET created_by = m.user_id FROM _user_email_map m WHERE ui_menu_item.created_by = m.email;
UPDATE ui_menu_item     SET updated_by = m.user_id FROM _user_email_map m WHERE ui_menu_item.updated_by = m.email;

UPDATE page_layout      SET created_by = m.user_id FROM _user_email_map m WHERE page_layout.created_by = m.email;
UPDATE page_layout      SET updated_by = m.user_id FROM _user_email_map m WHERE page_layout.updated_by = m.email;

UPDATE layout_section   SET created_by = m.user_id FROM _user_email_map m WHERE layout_section.created_by = m.email;
UPDATE layout_section   SET updated_by = m.user_id FROM _user_email_map m WHERE layout_section.updated_by = m.email;

UPDATE layout_field     SET created_by = m.user_id FROM _user_email_map m WHERE layout_field.created_by = m.email;
UPDATE layout_field     SET updated_by = m.user_id FROM _user_email_map m WHERE layout_field.updated_by = m.email;

UPDATE layout_related_list SET created_by = m.user_id FROM _user_email_map m WHERE layout_related_list.created_by = m.email;
UPDATE layout_related_list SET updated_by = m.user_id FROM _user_email_map m WHERE layout_related_list.updated_by = m.email;

UPDATE layout_assignment SET created_by = m.user_id FROM _user_email_map m WHERE layout_assignment.created_by = m.email;
UPDATE layout_assignment SET updated_by = m.user_id FROM _user_email_map m WHERE layout_assignment.updated_by = m.email;

UPDATE list_view        SET created_by = m.user_id FROM _user_email_map m WHERE list_view.created_by = m.email;
UPDATE list_view        SET updated_by = m.user_id FROM _user_email_map m WHERE list_view.updated_by = m.email;

-- Picklists
UPDATE global_picklist  SET created_by = m.user_id FROM _user_email_map m WHERE global_picklist.created_by = m.email;
UPDATE global_picklist  SET updated_by = m.user_id FROM _user_email_map m WHERE global_picklist.updated_by = m.email;

UPDATE picklist_value   SET created_by = m.user_id FROM _user_email_map m WHERE picklist_value.created_by = m.email;
UPDATE picklist_value   SET updated_by = m.user_id FROM _user_email_map m WHERE picklist_value.updated_by = m.email;

UPDATE picklist_dependency SET created_by = m.user_id FROM _user_email_map m WHERE picklist_dependency.created_by = m.email;
UPDATE picklist_dependency SET updated_by = m.user_id FROM _user_email_map m WHERE picklist_dependency.updated_by = m.email;

UPDATE record_type      SET created_by = m.user_id FROM _user_email_map m WHERE record_type.created_by = m.email;
UPDATE record_type      SET updated_by = m.user_id FROM _user_email_map m WHERE record_type.updated_by = m.email;

UPDATE record_type_picklist SET created_by = m.user_id FROM _user_email_map m WHERE record_type_picklist.created_by = m.email;
UPDATE record_type_picklist SET updated_by = m.user_id FROM _user_email_map m WHERE record_type_picklist.updated_by = m.email;

UPDATE validation_rule  SET created_by = m.user_id FROM _user_email_map m WHERE validation_rule.created_by = m.email;
UPDATE validation_rule  SET updated_by = m.user_id FROM _user_email_map m WHERE validation_rule.updated_by = m.email;

-- Workflows & automation (workflow_rule, workflow_action_type, workflow_action,
-- workflow_pending_action, workflow_rule_version tables not yet created — skipped)

UPDATE script           SET created_by = m.user_id FROM _user_email_map m WHERE script.created_by = m.email;
UPDATE script           SET updated_by = m.user_id FROM _user_email_map m WHERE script.updated_by = m.email;

UPDATE script_trigger   SET created_by = m.user_id FROM _user_email_map m WHERE script_trigger.created_by = m.email;
UPDATE script_trigger   SET updated_by = m.user_id FROM _user_email_map m WHERE script_trigger.updated_by = m.email;

UPDATE flow             SET created_by = m.user_id FROM _user_email_map m WHERE flow.created_by = m.email;
UPDATE flow             SET updated_by = m.user_id FROM _user_email_map m WHERE flow.updated_by = m.email;

UPDATE approval_process SET created_by = m.user_id FROM _user_email_map m WHERE approval_process.created_by = m.email;
UPDATE approval_process SET updated_by = m.user_id FROM _user_email_map m WHERE approval_process.updated_by = m.email;

UPDATE approval_step    SET created_by = m.user_id FROM _user_email_map m WHERE approval_step.created_by = m.email;
UPDATE approval_step    SET updated_by = m.user_id FROM _user_email_map m WHERE approval_step.updated_by = m.email;

UPDATE approval_instance SET created_by = m.user_id FROM _user_email_map m WHERE approval_instance.created_by = m.email;
UPDATE approval_instance SET updated_by = m.user_id FROM _user_email_map m WHERE approval_instance.updated_by = m.email;

UPDATE approval_step_instance SET created_by = m.user_id FROM _user_email_map m WHERE approval_step_instance.created_by = m.email;
UPDATE approval_step_instance SET updated_by = m.user_id FROM _user_email_map m WHERE approval_step_instance.updated_by = m.email;

UPDATE scheduled_job    SET created_by = m.user_id FROM _user_email_map m WHERE scheduled_job.created_by = m.email;
UPDATE scheduled_job    SET updated_by = m.user_id FROM _user_email_map m WHERE scheduled_job.updated_by = m.email;

-- Communication
UPDATE email_template   SET created_by = m.user_id FROM _user_email_map m WHERE email_template.created_by = m.email;
UPDATE email_template   SET updated_by = m.user_id FROM _user_email_map m WHERE email_template.updated_by = m.email;

UPDATE webhook          SET created_by = m.user_id FROM _user_email_map m WHERE webhook.created_by = m.email;
UPDATE webhook          SET updated_by = m.user_id FROM _user_email_map m WHERE webhook.updated_by = m.email;

-- Integration
UPDATE connected_app    SET created_by = m.user_id FROM _user_email_map m WHERE connected_app.created_by = m.email;
UPDATE connected_app    SET updated_by = m.user_id FROM _user_email_map m WHERE connected_app.updated_by = m.email;

UPDATE connected_app_token SET created_by = m.user_id FROM _user_email_map m WHERE connected_app_token.created_by = m.email;
UPDATE connected_app_token SET updated_by = m.user_id FROM _user_email_map m WHERE connected_app_token.updated_by = m.email;

UPDATE oidc_provider    SET created_by = m.user_id FROM _user_email_map m WHERE oidc_provider.created_by = m.email;
UPDATE oidc_provider    SET updated_by = m.user_id FROM _user_email_map m WHERE oidc_provider.updated_by = m.email;

-- Reports & dashboards
UPDATE report           SET created_by = m.user_id FROM _user_email_map m WHERE report.created_by = m.email;
UPDATE report           SET updated_by = m.user_id FROM _user_email_map m WHERE report.updated_by = m.email;

UPDATE report_folder    SET created_by = m.user_id FROM _user_email_map m WHERE report_folder.created_by = m.email;
UPDATE report_folder    SET updated_by = m.user_id FROM _user_email_map m WHERE report_folder.updated_by = m.email;

UPDATE dashboard        SET created_by = m.user_id FROM _user_email_map m WHERE dashboard.created_by = m.email;
UPDATE dashboard        SET updated_by = m.user_id FROM _user_email_map m WHERE dashboard.updated_by = m.email;

UPDATE dashboard_component SET created_by = m.user_id FROM _user_email_map m WHERE dashboard_component.created_by = m.email;
UPDATE dashboard_component SET updated_by = m.user_id FROM _user_email_map m WHERE dashboard_component.updated_by = m.email;

-- Collaboration
UPDATE note             SET created_by = m.user_id FROM _user_email_map m WHERE note.created_by = m.email;
UPDATE note             SET updated_by = m.user_id FROM _user_email_map m WHERE note.updated_by = m.email;

UPDATE file_attachment  SET created_by = m.user_id FROM _user_email_map m WHERE file_attachment.created_by = m.email;
UPDATE file_attachment  SET updated_by = m.user_id FROM _user_email_map m WHERE file_attachment.updated_by = m.email;

-- Platform management
UPDATE bulk_job         SET created_by = m.user_id FROM _user_email_map m WHERE bulk_job.created_by = m.email;
UPDATE bulk_job         SET updated_by = m.user_id FROM _user_email_map m WHERE bulk_job.updated_by = m.email;

UPDATE package          SET created_by = m.user_id FROM _user_email_map m WHERE package.created_by = m.email;
UPDATE package          SET updated_by = m.user_id FROM _user_email_map m WHERE package.updated_by = m.email;

UPDATE package_item     SET created_by = m.user_id FROM _user_email_map m WHERE package_item.created_by = m.email;
UPDATE package_item     SET updated_by = m.user_id FROM _user_email_map m WHERE package_item.updated_by = m.email;

-- platform_user: self-referential — resolve created_by/updated_by to another user's UUID
UPDATE platform_user    SET created_by = m.user_id FROM _user_email_map m WHERE platform_user.created_by = m.email;
UPDATE platform_user    SET updated_by = m.user_id FROM _user_email_map m WHERE platform_user.updated_by = m.email;

-- flow_version: no tenant_id column, match on email directly
UPDATE flow_version     SET created_by = m.user_id FROM _user_email_map m WHERE flow_version.created_by = m.email;
UPDATE flow_version     SET updated_by = m.user_id FROM _user_email_map m WHERE flow_version.updated_by = m.email;

-- Audit / log tables
UPDATE security_audit_log   SET created_by = m.user_id FROM _user_email_map m WHERE security_audit_log.created_by = m.email;
UPDATE security_audit_log   SET updated_by = m.user_id FROM _user_email_map m WHERE security_audit_log.updated_by = m.email;

UPDATE setup_audit_trail    SET created_by = m.user_id FROM _user_email_map m WHERE setup_audit_trail.created_by = m.email;
UPDATE setup_audit_trail    SET updated_by = m.user_id FROM _user_email_map m WHERE setup_audit_trail.updated_by = m.email;

UPDATE field_history        SET created_by = m.user_id FROM _user_email_map m WHERE field_history.created_by = m.email;
UPDATE field_history        SET updated_by = m.user_id FROM _user_email_map m WHERE field_history.updated_by = m.email;

-- workflow_execution_log, workflow_action_log tables not yet created — skipped

UPDATE script_execution_log SET created_by = m.user_id FROM _user_email_map m WHERE script_execution_log.created_by = m.email;
UPDATE script_execution_log SET updated_by = m.user_id FROM _user_email_map m WHERE script_execution_log.updated_by = m.email;

UPDATE email_log            SET created_by = m.user_id FROM _user_email_map m WHERE email_log.created_by = m.email;
UPDATE email_log            SET updated_by = m.user_id FROM _user_email_map m WHERE email_log.updated_by = m.email;

UPDATE webhook_delivery     SET created_by = m.user_id FROM _user_email_map m WHERE webhook_delivery.created_by = m.email;
UPDATE webhook_delivery     SET updated_by = m.user_id FROM _user_email_map m WHERE webhook_delivery.updated_by = m.email;

UPDATE login_history        SET created_by = m.user_id FROM _user_email_map m WHERE login_history.created_by = m.email;
UPDATE login_history        SET updated_by = m.user_id FROM _user_email_map m WHERE login_history.updated_by = m.email;

UPDATE flow_execution       SET created_by = m.user_id FROM _user_email_map m WHERE flow_execution.created_by = m.email;
UPDATE flow_execution       SET updated_by = m.user_id FROM _user_email_map m WHERE flow_execution.updated_by = m.email;

UPDATE job_execution_log    SET created_by = m.user_id FROM _user_email_map m WHERE job_execution_log.created_by = m.email;
UPDATE job_execution_log    SET updated_by = m.user_id FROM _user_email_map m WHERE job_execution_log.updated_by = m.email;

UPDATE bulk_job_result      SET created_by = m.user_id FROM _user_email_map m WHERE bulk_job_result.created_by = m.email;
UPDATE bulk_job_result      SET updated_by = m.user_id FROM _user_email_map m WHERE bulk_job_result.updated_by = m.email;

UPDATE migration_run        SET created_by = m.user_id FROM _user_email_map m WHERE migration_run.created_by = m.email;
UPDATE migration_run        SET updated_by = m.user_id FROM _user_email_map m WHERE migration_run.updated_by = m.email;

UPDATE migration_step       SET created_by = m.user_id FROM _user_email_map m WHERE migration_step.created_by = m.email;
UPDATE migration_step       SET updated_by = m.user_id FROM _user_email_map m WHERE migration_step.updated_by = m.email;

-- Cleanup temp table
DROP TABLE _user_email_map;

-- =========================================================================
-- Step 2: Drop existing inline FK constraints on created_by (from V22-V29)
-- =========================================================================
-- These were created as unnamed inline constraints.  PostgreSQL names them
-- {table}_{column}_fkey by convention.

ALTER TABLE list_view        DROP CONSTRAINT IF EXISTS list_view_created_by_fkey;
ALTER TABLE report_folder    DROP CONSTRAINT IF EXISTS report_folder_created_by_fkey;
ALTER TABLE report           DROP CONSTRAINT IF EXISTS report_created_by_fkey;
ALTER TABLE flow             DROP CONSTRAINT IF EXISTS flow_created_by_fkey;
ALTER TABLE dashboard        DROP CONSTRAINT IF EXISTS dashboard_created_by_fkey;
ALTER TABLE email_template   DROP CONSTRAINT IF EXISTS email_template_created_by_fkey;
ALTER TABLE scheduled_job    DROP CONSTRAINT IF EXISTS scheduled_job_created_by_fkey;

-- =========================================================================
-- Step 3: Null out any remaining unresolvable values (emails that had no
-- matching platform_user) so FK constraints can be added.
-- Only affects rows where created_by/updated_by still contains '@'.
-- =========================================================================

-- Core entities
UPDATE collection            SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE collection            SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE field                 SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE field                 SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE collection_version    SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE collection_version    SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE field_version         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE field_version         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE profile               SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE profile               SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE permission_set        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE permission_set        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE permset_system_permission SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE permset_system_permission SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE permset_object_permission SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE permset_object_permission SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE permset_field_permission  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE permset_field_permission  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE profile_system_permission SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE profile_system_permission SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE profile_object_permission SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE profile_object_permission SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE profile_field_permission  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE profile_field_permission  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE user_group            SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE user_group            SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE group_membership      SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE group_membership      SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE user_permission_set   SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE user_permission_set   SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE group_permission_set  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE group_permission_set  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE ui_page               SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE ui_page               SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE ui_menu               SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE ui_menu               SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE ui_menu_item          SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE ui_menu_item          SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE page_layout           SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE page_layout           SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE layout_section        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE layout_section        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE layout_field          SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE layout_field          SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE layout_related_list   SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE layout_related_list   SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE layout_assignment     SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE layout_assignment     SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE list_view             SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE list_view             SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE global_picklist       SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE global_picklist       SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE picklist_value        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE picklist_value        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE picklist_dependency   SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE picklist_dependency   SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE record_type           SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE record_type           SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE record_type_picklist  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE record_type_picklist  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE validation_rule       SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE validation_rule       SET updated_by = NULL WHERE updated_by LIKE '%@%';
-- workflow_rule, workflow_action_type, workflow_action, workflow_pending_action,
-- workflow_rule_version tables not yet created — skipped
UPDATE script                SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE script                SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE script_trigger        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE script_trigger        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE flow                  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE flow                  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE approval_process      SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE approval_process      SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE approval_step         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE approval_step         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE approval_instance     SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE approval_instance     SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE approval_step_instance SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE approval_step_instance SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE scheduled_job         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE scheduled_job         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE email_template        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE email_template        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE webhook               SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE webhook               SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE connected_app         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE connected_app         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE connected_app_token   SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE connected_app_token   SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE oidc_provider         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE oidc_provider         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE report                SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE report                SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE report_folder         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE report_folder         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE dashboard             SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE dashboard             SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE dashboard_component   SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE dashboard_component   SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE note                  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE note                  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE file_attachment       SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE file_attachment       SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE bulk_job              SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE bulk_job              SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE package               SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE package               SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE package_item          SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE package_item          SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE platform_user         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE platform_user         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE tenant                SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE tenant                SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE migration_run         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE migration_run         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE migration_step        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE migration_step        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE security_audit_log    SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE security_audit_log    SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE setup_audit_trail     SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE setup_audit_trail     SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE field_history         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE field_history         SET updated_by = NULL WHERE updated_by LIKE '%@%';
-- workflow_execution_log, workflow_action_log tables not yet created — skipped
UPDATE script_execution_log  SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE script_execution_log  SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE email_log             SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE email_log             SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE webhook_delivery      SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE webhook_delivery      SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE login_history         SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE login_history         SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE flow_execution        SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE flow_execution        SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE flow_version          SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE job_execution_log     SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE job_execution_log     SET updated_by = NULL WHERE updated_by LIKE '%@%';
UPDATE bulk_job_result       SET created_by = NULL WHERE created_by LIKE '%@%';
UPDATE bulk_job_result       SET updated_by = NULL WHERE updated_by LIKE '%@%';

-- Also null out the 'system-migration' value used by WorkflowMigrationController
-- so it doesn't block FK constraints (it's not a valid platform_user ID)
UPDATE flow SET created_by = NULL WHERE created_by = 'system-migration';

-- =========================================================================
-- Step 4: Drop NOT NULL on created_by where it exists (from original V22-V29
-- CREATE TABLE statements) — these columns are now nullable to allow
-- system-created records without a user context.
-- =========================================================================

ALTER TABLE list_view        ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE report_folder    ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE report           ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE flow             ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE dashboard        ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE email_template   ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE scheduled_job    ALTER COLUMN created_by DROP NOT NULL;

-- =========================================================================
-- Step 5: Add named FK constraints on created_by and updated_by
-- for all tenant-scoped tables.
-- =========================================================================

-- Core entities
ALTER TABLE collection            ADD CONSTRAINT fk_collection_created_by            FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE collection            ADD CONSTRAINT fk_collection_updated_by            FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE field                 ADD CONSTRAINT fk_field_created_by                 FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE field                 ADD CONSTRAINT fk_field_updated_by                 FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE collection_version    ADD CONSTRAINT fk_collection_version_created_by    FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE collection_version    ADD CONSTRAINT fk_collection_version_updated_by    FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE field_version         ADD CONSTRAINT fk_field_version_created_by         FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE field_version         ADD CONSTRAINT fk_field_version_updated_by         FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Security & permissions
ALTER TABLE profile               ADD CONSTRAINT fk_profile_created_by               FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE profile               ADD CONSTRAINT fk_profile_updated_by               FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE permission_set        ADD CONSTRAINT fk_permission_set_created_by        FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE permission_set        ADD CONSTRAINT fk_permission_set_updated_by        FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE user_group            ADD CONSTRAINT fk_user_group_created_by            FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE user_group            ADD CONSTRAINT fk_user_group_updated_by            FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- UI & layouts
ALTER TABLE ui_page               ADD CONSTRAINT fk_ui_page_created_by               FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE ui_page               ADD CONSTRAINT fk_ui_page_updated_by               FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE page_layout           ADD CONSTRAINT fk_page_layout_created_by           FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE page_layout           ADD CONSTRAINT fk_page_layout_updated_by           FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE list_view             ADD CONSTRAINT fk_list_view_created_by             FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE list_view             ADD CONSTRAINT fk_list_view_updated_by             FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Picklists & record types
ALTER TABLE global_picklist       ADD CONSTRAINT fk_global_picklist_created_by       FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE global_picklist       ADD CONSTRAINT fk_global_picklist_updated_by       FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE record_type           ADD CONSTRAINT fk_record_type_created_by           FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE record_type           ADD CONSTRAINT fk_record_type_updated_by           FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE validation_rule       ADD CONSTRAINT fk_validation_rule_created_by       FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE validation_rule       ADD CONSTRAINT fk_validation_rule_updated_by       FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Workflows & automation
-- workflow_rule not yet created — FK constraints skipped
ALTER TABLE script                ADD CONSTRAINT fk_script_created_by               FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE script                ADD CONSTRAINT fk_script_updated_by               FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE flow                  ADD CONSTRAINT fk_flow_created_by                 FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE flow                  ADD CONSTRAINT fk_flow_updated_by                 FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE scheduled_job         ADD CONSTRAINT fk_scheduled_job_created_by        FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE scheduled_job         ADD CONSTRAINT fk_scheduled_job_updated_by        FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Communication
ALTER TABLE email_template        ADD CONSTRAINT fk_email_template_created_by       FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE email_template        ADD CONSTRAINT fk_email_template_updated_by       FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE webhook               ADD CONSTRAINT fk_webhook_created_by              FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE webhook               ADD CONSTRAINT fk_webhook_updated_by              FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Integration
ALTER TABLE connected_app         ADD CONSTRAINT fk_connected_app_created_by        FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE connected_app         ADD CONSTRAINT fk_connected_app_updated_by        FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Reports & dashboards
ALTER TABLE report                ADD CONSTRAINT fk_report_created_by               FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE report                ADD CONSTRAINT fk_report_updated_by               FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE report_folder         ADD CONSTRAINT fk_report_folder_created_by        FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE report_folder         ADD CONSTRAINT fk_report_folder_updated_by        FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE dashboard             ADD CONSTRAINT fk_dashboard_created_by            FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE dashboard             ADD CONSTRAINT fk_dashboard_updated_by            FOREIGN KEY (updated_by) REFERENCES platform_user(id);

-- Collaboration
ALTER TABLE note                  ADD CONSTRAINT fk_note_created_by                 FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE note                  ADD CONSTRAINT fk_note_updated_by                 FOREIGN KEY (updated_by) REFERENCES platform_user(id);
ALTER TABLE file_attachment       ADD CONSTRAINT fk_file_attachment_created_by      FOREIGN KEY (created_by) REFERENCES platform_user(id);
ALTER TABLE file_attachment       ADD CONSTRAINT fk_file_attachment_updated_by      FOREIGN KEY (updated_by) REFERENCES platform_user(id);
