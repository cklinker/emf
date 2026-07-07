-- V94: Assign default profile to users who have no profile assigned.
--
-- Users auto-provisioned by LoginTrackingFilter after V55 were created
-- without a profile_id, leaving them with zero system permissions.
-- This migration assigns "System Administrator" to all such users,
-- matching the V55 seed behaviour for pre-existing users.

UPDATE platform_user u
SET    profile_id = (
           SELECT p.id
           FROM   profile p
           WHERE  p.tenant_id = u.tenant_id
             AND  p.name = 'System Administrator'
           LIMIT  1
       ),
       updated_at = NOW()
WHERE  u.profile_id IS NULL
  AND  u.status = 'ACTIVE';
