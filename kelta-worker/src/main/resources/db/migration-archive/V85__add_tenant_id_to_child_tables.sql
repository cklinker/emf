-- ============================================================================
-- V85: Add tenant_id to child/junction tables for full tenant isolation
-- ============================================================================
-- These 16 tables previously relied on FK cascading from parent tables for
-- tenant isolation. Adding tenant_id directly enables Row Level Security
-- and simplifies tenant-scoped queries.
--
-- Pattern per table:
--   1. ALTER TABLE ADD COLUMN tenant_id (nullable)
--   2. UPDATE ... SET tenant_id from parent table join
--   3. ALTER TABLE SET NOT NULL
--   4. ADD FOREIGN KEY to tenant table
--   5. CREATE INDEX on tenant_id
-- ============================================================================

-- -------------------------------------------------------------------------
-- 1. picklist_value (parent: field → collection OR global_picklist)
-- -------------------------------------------------------------------------
ALTER TABLE picklist_value ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE picklist_value pv
SET tenant_id = COALESCE(
    (SELECT c.tenant_id FROM field f JOIN collection c ON f.collection_id = c.id
     WHERE f.id = pv.picklist_source_id AND pv.picklist_source_type = 'FIELD'),
    (SELECT gp.tenant_id FROM global_picklist gp
     WHERE gp.id = pv.picklist_source_id AND pv.picklist_source_type = 'GLOBAL')
)
WHERE pv.tenant_id IS NULL;

ALTER TABLE picklist_value ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE picklist_value ADD CONSTRAINT fk_picklist_value_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_picklist_value_tenant_id ON picklist_value(tenant_id);

-- -------------------------------------------------------------------------
-- 2. picklist_dependency (parent: field → collection)
-- -------------------------------------------------------------------------
ALTER TABLE picklist_dependency ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE picklist_dependency pd
SET tenant_id = (
    SELECT c.tenant_id FROM field f JOIN collection c ON f.collection_id = c.id
    WHERE f.id = pd.controlling_field_id
)
WHERE pd.tenant_id IS NULL;

ALTER TABLE picklist_dependency ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE picklist_dependency ADD CONSTRAINT fk_picklist_dependency_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_picklist_dependency_tenant_id ON picklist_dependency(tenant_id);

-- -------------------------------------------------------------------------
-- 3. layout_section (parent: page_layout)
-- -------------------------------------------------------------------------
ALTER TABLE layout_section ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE layout_section ls
SET tenant_id = (SELECT pl.tenant_id FROM page_layout pl WHERE pl.id = ls.layout_id)
WHERE ls.tenant_id IS NULL;

ALTER TABLE layout_section ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE layout_section ADD CONSTRAINT fk_layout_section_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_layout_section_tenant_id ON layout_section(tenant_id);

-- -------------------------------------------------------------------------
-- 4. layout_field (parent: layout_section → page_layout)
-- -------------------------------------------------------------------------
ALTER TABLE layout_field ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE layout_field lf
SET tenant_id = (
    SELECT pl.tenant_id FROM layout_section ls
    JOIN page_layout pl ON pl.id = ls.layout_id
    WHERE ls.id = lf.section_id
)
WHERE lf.tenant_id IS NULL;

ALTER TABLE layout_field ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE layout_field ADD CONSTRAINT fk_layout_field_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_layout_field_tenant_id ON layout_field(tenant_id);

-- -------------------------------------------------------------------------
-- 5. layout_related_list (parent: page_layout)
-- -------------------------------------------------------------------------
ALTER TABLE layout_related_list ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE layout_related_list lrl
SET tenant_id = (SELECT pl.tenant_id FROM page_layout pl WHERE pl.id = lrl.layout_id)
WHERE lrl.tenant_id IS NULL;

ALTER TABLE layout_related_list ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE layout_related_list ADD CONSTRAINT fk_layout_related_list_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_layout_related_list_tenant_id ON layout_related_list(tenant_id);

-- -------------------------------------------------------------------------
-- 6. dashboard_component (parent: dashboard)
-- -------------------------------------------------------------------------
ALTER TABLE dashboard_component ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE dashboard_component dc
SET tenant_id = (SELECT d.tenant_id FROM dashboard d WHERE d.id = dc.dashboard_id)
WHERE dc.tenant_id IS NULL;

ALTER TABLE dashboard_component ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE dashboard_component ADD CONSTRAINT fk_dashboard_component_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_dashboard_component_tenant_id ON dashboard_component(tenant_id);

-- -------------------------------------------------------------------------
-- 7. profile_system_permission (parent: profile)
-- -------------------------------------------------------------------------
ALTER TABLE profile_system_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE profile_system_permission psp
SET tenant_id = (SELECT p.tenant_id FROM profile p WHERE p.id = psp.profile_id)
WHERE psp.tenant_id IS NULL;

ALTER TABLE profile_system_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE profile_system_permission ADD CONSTRAINT fk_profile_system_permission_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_profile_system_permission_tenant_id ON profile_system_permission(tenant_id);

-- -------------------------------------------------------------------------
-- 8. profile_object_permission (parent: profile)
-- -------------------------------------------------------------------------
ALTER TABLE profile_object_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE profile_object_permission pop
SET tenant_id = (SELECT p.tenant_id FROM profile p WHERE p.id = pop.profile_id)
WHERE pop.tenant_id IS NULL;

ALTER TABLE profile_object_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE profile_object_permission ADD CONSTRAINT fk_profile_object_permission_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_profile_object_permission_tenant_id ON profile_object_permission(tenant_id);

-- -------------------------------------------------------------------------
-- 9. profile_field_permission (parent: profile)
-- -------------------------------------------------------------------------
ALTER TABLE profile_field_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE profile_field_permission pfp
SET tenant_id = (SELECT p.tenant_id FROM profile p WHERE p.id = pfp.profile_id)
WHERE pfp.tenant_id IS NULL;

ALTER TABLE profile_field_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE profile_field_permission ADD CONSTRAINT fk_profile_field_permission_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_profile_field_permission_tenant_id ON profile_field_permission(tenant_id);

-- -------------------------------------------------------------------------
-- 10. permset_system_permission (parent: permission_set)
-- -------------------------------------------------------------------------
ALTER TABLE permset_system_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE permset_system_permission psp
SET tenant_id = (SELECT ps.tenant_id FROM permission_set ps WHERE ps.id = psp.permission_set_id)
WHERE psp.tenant_id IS NULL;

ALTER TABLE permset_system_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE permset_system_permission ADD CONSTRAINT fk_permset_system_permission_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_permset_system_permission_tenant_id ON permset_system_permission(tenant_id);

-- -------------------------------------------------------------------------
-- 11. permset_object_permission (parent: permission_set)
-- -------------------------------------------------------------------------
ALTER TABLE permset_object_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE permset_object_permission pop
SET tenant_id = (SELECT ps.tenant_id FROM permission_set ps WHERE ps.id = pop.permission_set_id)
WHERE pop.tenant_id IS NULL;

ALTER TABLE permset_object_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE permset_object_permission ADD CONSTRAINT fk_permset_object_permission_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_permset_object_permission_tenant_id ON permset_object_permission(tenant_id);

-- -------------------------------------------------------------------------
-- 12. permset_field_permission (parent: permission_set)
-- -------------------------------------------------------------------------
ALTER TABLE permset_field_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE permset_field_permission pfp
SET tenant_id = (SELECT ps.tenant_id FROM permission_set ps WHERE ps.id = pfp.permission_set_id)
WHERE pfp.tenant_id IS NULL;

ALTER TABLE permset_field_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE permset_field_permission ADD CONSTRAINT fk_permset_field_permission_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_permset_field_permission_tenant_id ON permset_field_permission(tenant_id);

-- -------------------------------------------------------------------------
-- 13. group_membership (parent: user_group)
-- -------------------------------------------------------------------------
ALTER TABLE group_membership ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE group_membership gm
SET tenant_id = (SELECT ug.tenant_id FROM user_group ug WHERE ug.id = gm.group_id)
WHERE gm.tenant_id IS NULL;

ALTER TABLE group_membership ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE group_membership ADD CONSTRAINT fk_group_membership_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_group_membership_tenant_id ON group_membership(tenant_id);

-- -------------------------------------------------------------------------
-- 14. group_permission_set (parent: user_group)
-- -------------------------------------------------------------------------
ALTER TABLE group_permission_set ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE group_permission_set gps
SET tenant_id = (SELECT ug.tenant_id FROM user_group ug WHERE ug.id = gps.group_id)
WHERE gps.tenant_id IS NULL;

ALTER TABLE group_permission_set ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE group_permission_set ADD CONSTRAINT fk_group_permission_set_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_group_permission_set_tenant_id ON group_permission_set(tenant_id);

-- -------------------------------------------------------------------------
-- 15. ui_menu_item (parent: ui_menu)
-- -------------------------------------------------------------------------
ALTER TABLE ui_menu_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE ui_menu_item umi
SET tenant_id = (SELECT um.tenant_id FROM ui_menu um WHERE um.id = umi.menu_id)
WHERE umi.tenant_id IS NULL;

ALTER TABLE ui_menu_item ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE ui_menu_item ADD CONSTRAINT fk_ui_menu_item_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_ui_menu_item_tenant_id ON ui_menu_item(tenant_id);

-- -------------------------------------------------------------------------
-- 16. tenant_module_action (parent: tenant_module)
-- -------------------------------------------------------------------------
ALTER TABLE tenant_module_action ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(36);

UPDATE tenant_module_action tma
SET tenant_id = (SELECT tm.tenant_id FROM tenant_module tm WHERE tm.id = tma.tenant_module_id)
WHERE tma.tenant_id IS NULL;

-- tenant_module_action may have zero rows; only set NOT NULL if column was added
ALTER TABLE tenant_module_action ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE tenant_module_action ADD CONSTRAINT fk_tenant_module_action_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
CREATE INDEX IF NOT EXISTS idx_tenant_module_action_tenant_id ON tenant_module_action(tenant_id);

-- Also add missing audit columns to tenant_module_action
ALTER TABLE tenant_module_action ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tenant_module_action ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE tenant_module_action ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

-- Backfill updated_at from created_at
UPDATE tenant_module_action SET updated_at = created_at WHERE updated_at IS NULL;
