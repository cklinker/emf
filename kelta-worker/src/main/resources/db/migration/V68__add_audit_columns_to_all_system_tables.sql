-- V68: Add missing audit columns (created_by, updated_by, updated_at) to all system tables
--
-- The PhysicalTableStorageAdapter unconditionally inserts/selects
-- id, created_at, updated_at, created_by, updated_by for every collection.
-- Many system tables were created before this convention and are missing
-- some or all of these columns.  This migration uses IF NOT EXISTS so it
-- is safe to run even if some columns were already added by earlier migrations.

-- =========================================================================
-- Core entity tables
-- =========================================================================

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE platform_user
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE profile
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE permission_set
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE collection
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE field
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- User groups & membership
-- =========================================================================

ALTER TABLE user_group
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE group_membership
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Permission tables (profile-based)
-- =========================================================================

ALTER TABLE profile_system_permission
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE profile_object_permission
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE profile_field_permission
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Permission tables (permission-set-based)
-- =========================================================================

ALTER TABLE permset_system_permission
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE permset_object_permission
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE permset_field_permission
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- UI & layout tables
-- =========================================================================

ALTER TABLE ui_page
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE ui_menu
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE ui_menu_item
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- List views (has created_by, needs updated_by)
-- =========================================================================

ALTER TABLE list_view
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Picklists
-- =========================================================================

ALTER TABLE global_picklist
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE picklist_value
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE picklist_dependency
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE record_type_picklist
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Record types & validation
-- =========================================================================

ALTER TABLE record_type
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE validation_rule
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Workflows & automation
-- =========================================================================

ALTER TABLE workflow_rule
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE workflow_action_type
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE workflow_action
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE workflow_pending_action
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE script
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE script_trigger
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE flow
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE approval_process
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE approval_step
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE approval_instance
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE approval_step_instance
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE scheduled_job
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Communication
-- =========================================================================

ALTER TABLE email_template
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE webhook
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Integration
-- =========================================================================

ALTER TABLE connected_app
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE connected_app_token
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE oidc_provider
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Reports & dashboards
-- =========================================================================

ALTER TABLE report
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE report_folder
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE dashboard
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE dashboard_component
    ADD COLUMN IF NOT EXISTS created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Collaboration
-- =========================================================================

ALTER TABLE note
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE file_attachment
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Platform management
-- =========================================================================

ALTER TABLE bulk_job
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE package
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE package_item
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE migration_run
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE migration_step
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Audit / log tables (read-only)
-- =========================================================================

ALTER TABLE security_audit_log
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE setup_audit_trail
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE field_history
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE workflow_execution_log
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE workflow_action_log
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE workflow_rule_version
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE script_execution_log
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE email_log
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE webhook_delivery
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE login_history
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE flow_execution
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE job_execution_log
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE bulk_job_result
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

-- =========================================================================
-- Versioning tables (read-only)
-- =========================================================================

ALTER TABLE collection_version
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);

ALTER TABLE field_version
    ADD COLUMN IF NOT EXISTS updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by  VARCHAR(255);
