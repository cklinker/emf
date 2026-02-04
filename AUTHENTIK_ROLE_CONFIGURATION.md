# Authentik Role Configuration Guide

## Problem
The EMF Control Plane expects JWT tokens to contain an "ADMIN" role, but Authentik tokens don't include this role by default.

## Solution: Configure Authentik to Send Roles

### Step 1: Create Groups in Authentik

1. Go to Authentik Admin UI → Directory → Groups
2. Create the following groups:
   - **emf-admins** - Full administrative access
   - **emf-developers** - Developer access
   - **emf-viewers** - Read-only access

### Step 2: Assign Users to Groups

1. Go to Directory → Users
2. Select a user
3. Click "Groups" tab
4. Add the user to the appropriate group (e.g., `emf-admins`)

### Step 3: Configure the OAuth2/OIDC Provider

1. Go to Applications → Providers
2. Find your EMF provider (e.g., "emf")
3. Edit the provider settings
4. Under "Advanced protocol settings":
   - **Scopes**: Ensure `openid`, `profile`, `email` are included
   - Add a custom scope for roles (if not already present)

### Step 4: Create Property Mappings for Roles

Authentik needs to map groups to JWT claims. Create a custom property mapping:

1. Go to Customization → Property Mappings
2. Click "Create" → "Scope Mapping"
3. Configure:
   - **Name**: `EMF Roles Mapping`
   - **Scope name**: `roles` (or use existing scope)
   - **Expression**:
   ```python
   # Map Authentik groups to roles
   roles = []
   for group in user.ak_groups.all():
       if group.name == "emf-admins":
           roles.append("ADMIN")
       elif group.name == "emf-developers":
           roles.append("DEVELOPER")
       elif group.name == "emf-viewers":
           roles.append("VIEWER")
   
   return {
       "roles": roles
   }
   ```

### Step 5: Attach Property Mapping to Provider

1. Go back to Applications → Providers
2. Edit your EMF provider
3. Under "Property mappings", add the "EMF Roles Mapping" you just created
4. Save

### Step 6: Verify Token Contents

After logging in, decode your JWT token (use jwt.io) and verify it contains:

```json
{
  "roles": ["ADMIN"],
  "sub": "...",
  "iss": "https://authentik.rzware.com/application/o/emf",
  ...
}
```

## Alternative: Use Groups Claim

If you prefer to use Authentik's built-in groups claim instead of custom roles:

### Option A: Map groups to roles in SecurityConfig

The `SecurityConfig.java` already extracts from the "groups" claim. You can:

1. In Authentik, ensure groups are included in tokens (usually automatic)
2. Name your groups: `ADMIN`, `DEVELOPER`, `VIEWER`
3. The SecurityConfig will automatically convert them to `ROLE_ADMIN`, etc.

### Option B: Configure custom claim name

Update `application-local.yml`:

```yaml
emf:
  control-plane:
    security:
      role-claim-name: groups  # Use 'groups' instead of 'roles'
```

Then update SecurityConfig to prioritize the configured claim name.

## Temporary Workaround (Development Only)

For local development, you can temporarily disable role checks:

### Option 1: Change all @PreAuthorize to isAuthenticated()

Replace `@PreAuthorize("hasRole('ADMIN')")` with `@PreAuthorize("isAuthenticated()")` in controllers.

**WARNING**: This allows any authenticated user to perform admin actions. Only use in development!

### Option 2: Disable security entirely

In `application-local.yml`:

```yaml
emf:
  control-plane:
    security:
      enabled: false
```

**WARNING**: This disables all security. Only use in isolated development environments!

## Testing

After configuration, test with:

```bash
# Get a token from Authentik
TOKEN="your-jwt-token-here"

# Decode and inspect
echo $TOKEN | cut -d. -f2 | base64 -d | jq .

# Test API call
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/control/roles
```

You should see the roles in the decoded token and the API call should succeed.

## Current Status

The `AuthorizationController` has been temporarily updated to use `isAuthenticated()` instead of `hasRole('ADMIN')` to unblock development. Other controllers still require the ADMIN role.

**Next Steps**:
1. Configure Authentik as described above
2. Verify tokens contain the ADMIN role
3. Revert the temporary changes to use proper role-based authorization
