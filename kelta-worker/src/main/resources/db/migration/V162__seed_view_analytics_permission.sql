-- Backfill VIEW_ANALYTICS for every existing profile, all tenants.
-- Runs under Flyway with app.current_tenant_id unset -> the admin_bypass RLS policy
-- (USING current_setting('app.current_tenant_id', true) = '') permits the cross-tenant write.
-- granted = profile already holds granted MANAGE_REPORTS, OR it is a built-in (is_system)
-- profile other than 'Minimum Access'. Idempotent via NOT EXISTS.
INSERT INTO profile_system_permission
    (id, tenant_id, profile_id, permission_name, granted, created_at, updated_at)
SELECT
    gen_random_uuid()::text,
    p.tenant_id,
    p.id,
    'VIEW_ANALYTICS',
    (EXISTS (SELECT 1 FROM profile_system_permission mr
             WHERE mr.profile_id = p.id
               AND mr.permission_name = 'MANAGE_REPORTS'
               AND mr.granted = true)
     OR (p.is_system = true AND p.name <> 'Minimum Access')),
    now(), now()
FROM profile p
WHERE NOT EXISTS (SELECT 1 FROM profile_system_permission x
                  WHERE x.profile_id = p.id
                    AND x.permission_name = 'VIEW_ANALYTICS');
