# Authentik Setup Guide

## Problem Summary
You were experiencing a login loop because:
1. The UI couldn't fetch OIDC providers from the backend (CORS issue)
2. The database had a Keycloak provider, but you switched to Authentik
3. The provider ID mismatch caused authentication failures

## Changes Made

### 1. Database Migration (V3__add_authentik_provider.sql)
- Created a new migration to add Authentik as an OIDC provider
- Deactivates the old Keycloak provider
- Adds Authentik with ID `authentik`

### 2. Backend Configuration (application-local.yml)
- Updated JWT issuer URI to Authentik format
- Updated JWKS URI to Authentik format

### 3. CORS Configuration (SecurityConfig.java)
- Added CORS support for local development
- Allows requests from localhost:5173 (Vite), localhost:3000 (React), and localhost:8080

## Next Steps

### 1. Restart the Backend
The backend needs to be restarted to:
- Apply the new database migration (V3)
- Load the new CORS configuration
- Use the updated Authentik issuer URLs

```bash
cd emf-control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 2. Verify Authentik Configuration
Make sure your Authentik instance is configured with:

**Application Settings:**
- Client ID: `emf-ui`
- Client Type: Public
- Redirect URIs: `http://localhost:5173/auth/callback` (or your UI URL)
- Post Logout Redirect URIs: `http://localhost:5173`

**Provider Settings:**
- Issuer URL should be: `http://localhost:8180/application/o/emf/`
- JWKS URL should be: `http://localhost:8180/application/o/emf/jwks/`

### 3. Verify Database Migration
After restarting, check that the migration ran:

```sql
SELECT * FROM oidc_provider WHERE active = true;
```

You should see the Authentik provider with ID `authentik`.

### 4. Test the Login Flow
1. Open the UI at `http://localhost:5173`
2. The UI should fetch the bootstrap config successfully
3. You should see the Authentik provider available for login
4. Click login and you should be redirected to Authentik

## Troubleshooting

### If you still see "Failed to fetch providers"
- Check browser console for CORS errors
- Verify the backend is running on port 8080
- Check backend logs for any errors

### If you see "Provider not found: emf"
- The old session data might be cached
- Clear browser sessionStorage: Open DevTools → Application → Session Storage → Clear
- Or use incognito mode

### If JWT validation fails
- Verify the issuer URL in Authentik matches exactly: `http://localhost:8180/application/o/emf/`
- Check that the JWKS endpoint is accessible: `curl http://localhost:8180/application/o/emf/jwks/`
- Verify the client ID in Authentik is `emf-ui`

## Migration File Details

The migration file will:
1. Set `active = false` for the `local-keycloak` provider
2. Insert or update the `authentik` provider with:
   - ID: `authentik`
   - Name: `Authentik`
   - Issuer: `http://localhost:8180/application/o/emf/`
   - JWKS URI: `http://localhost:8180/application/o/emf/jwks/`
   - Client ID: `emf-ui`
   - Active: `true`
