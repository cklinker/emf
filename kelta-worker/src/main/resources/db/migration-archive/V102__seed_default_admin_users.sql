-- =============================================================================
-- V102: Seed a default admin user for each tenant in the internal IdP
-- =============================================================================
-- Creates a platform_user + user_credential for each tenant that doesn't
-- already have an admin@kelta.local user. The user is assigned the
-- "System Administrator" profile and must change their password on first login.
--
-- Username: admin
-- Email:    admin@kelta.local
-- Password: password (BCrypt-hashed, force_change_on_login = TRUE)

DO $$
DECLARE
    t RECORD;
    admin_profile_id VARCHAR(36);
    new_user_id VARCHAR(36);
BEGIN
    FOR t IN SELECT id, slug FROM tenant WHERE status = 'ACTIVE'
    LOOP
        -- Skip if admin user already exists for this tenant
        IF EXISTS (
            SELECT 1 FROM platform_user
            WHERE tenant_id = t.id AND email = 'admin@kelta.local'
        ) THEN
            RAISE NOTICE 'Admin user already exists for tenant % (%)', t.slug, t.id;
            CONTINUE;
        END IF;

        -- Find the System Administrator profile for this tenant
        SELECT id INTO admin_profile_id
        FROM profile
        WHERE tenant_id = t.id AND name = 'System Administrator'
        LIMIT 1;

        IF admin_profile_id IS NULL THEN
            RAISE WARNING 'No System Administrator profile found for tenant % (%), skipping', t.slug, t.id;
            CONTINUE;
        END IF;

        -- Create platform_user
        new_user_id := gen_random_uuid()::text;

        INSERT INTO platform_user (id, tenant_id, email, username, first_name, last_name, status, profile_id, created_at, updated_at)
        VALUES (new_user_id, t.id, 'admin@kelta.local', 'admin', 'System', 'Administrator', 'ACTIVE', admin_profile_id, NOW(), NOW());

        -- Create user_credential with BCrypt hash of 'password' and force change on first login
        INSERT INTO user_credential (id, user_id, password_hash, force_change_on_login, created_at)
        VALUES (gen_random_uuid()::text, new_user_id, '$2a$10$zAQaSHX1XSR1bwUL3pz9EOzecplsxInVizZc9HwLf7xPluSiE1EP6', TRUE, NOW());

        RAISE NOTICE 'Created default admin user for tenant % (%)', t.slug, t.id;
    END LOOP;
END $$;
