-- What a module created, so uninstall and upgrade can be safe.
--
-- Nothing recorded which collection came from which module, so uninstall had no way to distinguish
-- "the module made this" from "this already existed and the module reused it", and no way to tell
-- whether the tenant has edited it since. The safe default was therefore to leave everything, which
-- means a removed module's collections, pages and menu items stay behind with no way to find them.
--
-- ownership     CREATED  the module made it; only these may ever be removed on uninstall
--               ADOPTED  it already existed; the module reused it and must never remove it
-- content_hash  the content at provisioning time. A different hash means the tenant has edited it
--               since, so uninstall leaves it alone and reports it rather than silently reverting
--               an admin's work.

CREATE TABLE module_provisioned_resource (
    id             varchar(36)  PRIMARY KEY,
    tenant_id      varchar(36)  NOT NULL,
    module_id      varchar(100) NOT NULL,
    module_version varchar(20)  NOT NULL,
    resource_type  varchar(40)  NOT NULL,
    natural_key    varchar(500) NOT NULL,
    resource_id    varchar(36),
    ownership      varchar(10)  NOT NULL,
    content_hash   varchar(64),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chk_mpr_ownership CHECK (ownership IN ('CREATED', 'ADOPTED')),
    CONSTRAINT uq_mpr_natural UNIQUE (tenant_id, module_id, resource_type, natural_key)
);

CREATE INDEX idx_mpr_tenant_module ON module_provisioned_resource (tenant_id, module_id);

ALTER TABLE module_provisioned_resource ENABLE ROW LEVEL SECURITY;
ALTER TABLE module_provisioned_resource FORCE ROW LEVEL SECURITY;

-- Two policies, matching every other tenant table (see V178): tenant_isolation for a bound
-- connection, admin_bypass for the platform-scoped work that runs with no tenant set.
CREATE POLICY tenant_isolation ON module_provisioned_resource
    USING (((tenant_id)::text = current_setting('app.current_tenant_id'::text, true)));

CREATE POLICY admin_bypass ON module_provisioned_resource
    USING ((current_setting('app.current_tenant_id'::text, true) = ''::text));
