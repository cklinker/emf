# Login Loop - Final Solution

## Summary
The login loop was caused by multiple issues that have now been fixed:

1. **CORS not configured** - Backend wasn't allowing requests from UI dev server
2. **Vite proxy on wrong port** - Proxy was configured for 5173 but running on 5174  
3. **React StrictMode double-render** - Causing state validation to fail
4. **Backend issuer mismatch** - Backend was configured for localhost but database had production Authentik

## Changes Made

### 1. Backend - CORS Configuration
**File:** `emf-control-plane/app/src/main/java/com/emf/controlplane/config/SecurityConfig.java`
- Added CORS configuration to allow requests from localhost:5173, 5174, 3000, 8080

### 2. Backend - Authentik Configuration  
**File:** `emf-control-plane/app/src/main/resources/application-local.yml`
- Updated issuer URI to: `https://authentik.rzware.com/application/o/emf`
- Updated JWKS URI to: `https://authentik.rzware.com/application/o/emf/jwks/`

### 3. Frontend - Vite Proxy
**File:** `emf-ui/app/vite.config.ts`
- Changed port from 5173 to 5174
- Proxy already configured to forward `/ui/*` to `http://localhost:8080`

### 4. Frontend - Auth Context
**File:** `emf-ui/app/src/context/AuthContext.tsx`
- Added flag to prevent double callback processing (React StrictMode issue)
- Added better error handling and logging
- Fixed state validation error handling

### 5. Database Migration
**File:** `emf-control-plane/app/src/main/resources/db/migration/V3__add_authentik_provider.sql`
- Deactivates old Keycloak provider
- Adds Authentik provider with correct configuration

## How to Fix

### Step 1: Restart Backend
```bash
cd emf-control-plane
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

This will:
- Apply the V3 migration (add Authentik provider)
- Load new CORS configuration
- Use correct Authentik issuer URLs

### Step 2: Restart Frontend
```bash
cd emf-ui/app
npm run dev
```

This will start Vite on port 5174 with proxy configured.

### Step 3: Clear Browser Data
**CRITICAL:** You must clear browser data to remove cached failures:

**Option A - Clear Session Storage:**
1. Open DevTools (F12)
2. Application tab → Session Storage → `http://localhost:5174`
3. Right-click → Clear
4. Refresh page

**Option B - Use Incognito/Private Window:**
1. Open new incognito/private window
2. Navigate to `http://localhost:5174/`

### Step 4: Verify Authentik Configuration
In your Authentik admin panel, verify the application settings:

**Application:**
- Name: EMF Control Plane (or similar)
- Client ID: `CHf8Ic1pJpmOp7FjuZJTG8F75AJMc5xmOPVcxPDG`
- Client Type: **Public**
- Redirect URIs: 
  - `http://localhost:5174/auth/callback`
  - `http://localhost:5173/auth/callback` (if you switch ports)
- Post Logout Redirect URIs:
  - `http://localhost:5174`
  - `http://localhost:5173`

**Provider:**
- Type: OAuth2/OpenID Provider
- Issuer: `https://authentik.rzware.com/application/o/emf`

## Testing the Flow

1. Open `http://localhost:5174/` in a fresh browser session
2. You should see a loading spinner
3. Bootstrap config loads → providers fetched
4. Since there's only one provider, login auto-triggers
5. Redirects to Authentik login
6. After login, redirects back to `http://localhost:5174/auth/callback?code=...`
7. Token exchange happens
8. User is authenticated → redirected to dashboard

## Troubleshooting

### Still seeing "Failed to fetch"?
- Check backend is running: `curl http://localhost:8080/ui/config/bootstrap`
- Check proxy is working: `curl http://localhost:5174/ui/config/bootstrap`
- Hard refresh browser: Cmd+Shift+R (Mac) or Ctrl+Shift+R (Windows/Linux)
- Check browser Network tab for the actual error

### Still seeing "Invalid state parameter"?
- Clear session storage completely
- Make sure you're not clicking back button after auth
- Check console for "doubleInvokeEffectsOnFiber" - this is React StrictMode (expected in dev)

### Token exchange fails?
- Verify Authentik client ID matches database
- Check Authentik application is set to "Public" client type
- Verify redirect URI in Authentik matches exactly
- Check backend logs for JWT validation errors

### JWT validation fails after successful login?
- Verify backend issuer URI matches Authentik: `https://authentik.rzware.com/application/o/emf`
- Test JWKS endpoint: `curl https://authentik.rzware.com/application/o/emf/jwks/`
- Check backend logs for specific JWT errors

## Quick Diagnostic Commands

```bash
# Test backend directly
curl http://localhost:8080/ui/config/bootstrap | jq .

# Test through proxy
curl http://localhost:5174/ui/config/bootstrap | jq .

# Test Authentik discovery
curl https://authentik.rzware.com/application/o/emf/.well-known/openid-configuration | jq .

# Test Authentik JWKS
curl https://authentik.rzware.com/application/o/emf/jwks/ | jq .

# Check backend is running
curl http://localhost:8080/actuator/health

# Check what's in the database
psql -h 192.168.0.6 -U emf -d emf_control_plane -c "SELECT id, name, issuer, active FROM oidc_provider;"
```

## Why This Happened

The root cause was a combination of:
1. Switching from Keycloak to Authentik without updating all configurations
2. CORS not being configured for local development
3. Vite dev server running on a different port than configured
4. React StrictMode in development causing effects to run twice

All of these issues compounded to create a perfect storm where:
- Bootstrap config couldn't be fetched (CORS + proxy)
- Even when it could be fetched, the callback would fail (state validation)
- The error handling would clear state and cause a loop
- Auto-login on the login page would immediately trigger another attempt

## Prevention

To avoid this in the future:
1. Always update ALL configurations when switching identity providers
2. Test the full OAuth flow in a clean browser session
3. Check both backend AND frontend logs when debugging auth issues
4. Remember that React StrictMode causes double-renders in development
