package com.emf.runtime.module.schema;

import com.emf.runtime.module.schema.hooks.*;
import com.emf.runtime.workflow.BeforeSaveHook;
import com.emf.runtime.workflow.module.EmfModule;

import java.util.List;
import java.util.function.Consumer;

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
 * <p>Optionally accepts a schema creation callback that is invoked after a new tenant
 * is created. When provided, the callback receives the tenant slug and should create
 * the PostgreSQL schema for tenant-level database isolation.
 *
 * @since 1.0.0
 */
public class SchemaLifecycleModule implements EmfModule {

    private final Consumer<String> tenantSchemaCallback;

    /**
     * Creates a module with no tenant schema creation callback.
     */
    public SchemaLifecycleModule() {
        this(null);
    }

    /**
     * Creates a module with a callback invoked after tenant creation to create
     * the PostgreSQL schema for tenant-level database isolation.
     *
     * @param tenantSchemaCallback receives the tenant slug; may be {@code null}
     */
    public SchemaLifecycleModule(Consumer<String> tenantSchemaCallback) {
        this.tenantSchemaCallback = tenantSchemaCallback;
    }

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
        TenantLifecycleHook tenantHook = new TenantLifecycleHook();
        if (tenantSchemaCallback != null) {
            tenantHook.setSchemaCreationCallback(tenantSchemaCallback);
        }
        return List.of(
            new CollectionLifecycleHook(),
            new FieldLifecycleHook(),
            tenantHook,
            new UserLifecycleHook(),
            new ProfileLifecycleHook()
        );
    }
}
