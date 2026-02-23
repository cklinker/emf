package com.emf.runtime.module.schema;

import com.emf.runtime.module.schema.hooks.*;
import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.module.EmfModule;

import java.util.List;

/**
 * EMF module that provides lifecycle hooks for schema system collections.
 *
 * <p>This module registers before-save hooks for the following system collections:
 * <ul>
 *   <li><b>collections</b> — Validates name format, sets defaults (version, active, path)</li>
 *   <li><b>fields</b> — Validates field name/type, checks reserved names, defaults active</li>
 *   <li><b>tenants</b> — Validates slug format, normalizes lowercase, defaults status</li>
 *   <li><b>users</b> — Validates email format, normalizes lowercase, defaults locale/timezone/status</li>
 *   <li><b>profiles</b> — Validates name, defaults system flag</li>
 * </ul>
 *
 * <p>All hooks are stateless with zero external dependencies. They perform pure
 * validation and default-setting logic.
 *
 * @since 1.0.0
 */
public class SchemaLifecycleModule implements EmfModule {

    @Override
    public String getId() {
        return "emf-schema";
    }

    @Override
    public String getName() {
        return "Schema Lifecycle Module";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<BeforeSaveHook> getBeforeSaveHooks() {
        return List.of(
            new CollectionLifecycleHook(),
            new FieldLifecycleHook(),
            new TenantLifecycleHook(),
            new UserLifecycleHook(),
            new ProfileLifecycleHook()
        );
    }
}
