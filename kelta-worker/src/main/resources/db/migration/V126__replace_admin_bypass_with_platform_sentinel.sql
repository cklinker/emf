-- ============================================================================
-- V126: Replace the empty-string admin_bypass RLS policy with an explicit
-- platform-sentinel bypass policy.
-- ============================================================================
-- The original bypass policy (V77/V86/V88) granted full access whenever
-- app.current_tenant_id was the empty string. That was fragile — any code
-- path that forgot to bind a tenant context silently executed under a
-- platform-wide bypass.
--
-- We now use an explicit reserved sentinel value '__platform__' that only
-- TenantContext.runAsPlatform() writes. The connection wrapper
-- (TenantAwareDataSource) fails closed on blank tenant context, so the
-- empty-string path is unreachable from application code.
--
-- For one release we keep the empty-string entry alongside the sentinel so
-- Flyway can continue to run in environments that haven't adopted the
-- platform wrapper yet. A follow-up migration will drop the empty-string
-- branch once all services are known to bind a real tenant or the sentinel.
-- ============================================================================

DO $$
DECLARE
    tbl TEXT;
    rls_tables TEXT[];
BEGIN
    -- Build the list from pg_policies so we cover every table currently
    -- carrying an admin_bypass policy — works for V77, V86, V88, and any
    -- other migrations that followed the same pattern.
    SELECT ARRAY(
        SELECT DISTINCT tablename
        FROM pg_policies
        WHERE schemaname = 'public' AND policyname = 'admin_bypass'
        ORDER BY tablename
    ) INTO rls_tables;

    IF array_length(rls_tables, 1) IS NULL THEN
        RAISE NOTICE 'V126: no admin_bypass policies found — nothing to migrate';
        RETURN;
    END IF;

    FOREACH tbl IN ARRAY rls_tables
    LOOP
        -- Drop the old empty-string bypass and any pre-existing sentinel
        -- policy (idempotent for partial re-runs).
        EXECUTE format('DROP POLICY IF EXISTS admin_bypass ON %I', tbl);
        EXECUTE format('DROP POLICY IF EXISTS platform_bypass ON %I', tbl);

        -- Platform-sentinel bypass: grants full access when the reserved
        -- '__platform__' value is bound (TenantContext.runAsPlatform).
        -- Empty string is kept for backward compatibility during rollout —
        -- a follow-up migration will drop it.
        EXECUTE format(
            'CREATE POLICY platform_bypass ON %I USING ('
                || 'current_setting(''app.current_tenant_id'', true) IN ('''', ''__platform__'')'
            || ')',
            tbl
        );

        RAISE NOTICE 'V126: platform_bypass installed on %', tbl;
    END LOOP;
END $$;
