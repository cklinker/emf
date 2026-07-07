-- Disable forced password change for the default admin user so automated
-- tools (e.g. test harness, local dev) can authenticate immediately
-- without an interactive password-change flow.
UPDATE user_credential
SET    force_change_on_login = FALSE
WHERE  user_id IN (
    SELECT pu.id
    FROM   platform_user pu
    WHERE  pu.email = 'admin@kelta.local'
);
