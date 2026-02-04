# Debug Login Loop - Checklist

## What to Check in Browser DevTools

### 1. Network Tab
Open DevTools (F12) → Network tab and look for:

**Bootstrap Config Request:**
- URL: `http://localhost:8080/ui/config/bootstrap`
- Status: Should be 200 OK
- Response: Should contain `oidcProviders` array with your Authentik provider

**OIDC Discovery Request:**
- URL: `https://authentik.rzware.com/application/o/emf/.well-known/openid-configuration`
- Status: Should be 200 OK
- Response: Should contain `authorization_endpoint`, `token_endpoint`, etc.

**Failed Requests:**
- Look for any requests with status 401, 403, 404, or CORS errors
- CORS errors show as "blocked by CORS policy" in the console

### 2. Console Tab
Look for errors like:
- `Failed to fetch providers` - Bootstrap config issue
- `Provider not found: emf` - Provider ID mismatch
- `CORS policy` - CORS configuration issue
- `Invalid state parameter` - OAuth state validation failed
- `Token exchange failed` - Token endpoint issue

### 3. Application Tab → Session Storage
Check for stored auth data:
- `emf_auth_tokens` - Should contain access_token, id_token, etc.
- `emf_auth_state` - OAuth state parameter
- `emf_auth_provider_id` - Should be "emf"
- `emf_auth_redirect_path` - Path to redirect after login

**If you see old data, clear it:**
Right-click on Session Storage → Clear

## Common Issues and Fixes

### Issue 1: CORS Error
**Symptom:** Console shows "blocked by CORS policy"
**Fix:** 
1. Restart backend (I just added port 5174 to CORS config)
2. Verify backend is running on port 8080

### Issue 2: Redirect URI Mismatch
**Symptom:** After Authentik login, you get an error or redirect fails
**Fix:** In Authentik, add this redirect URI:
- `http://localhost:5174/auth/callback`

### Issue 3: Provider Not Found
**Symptom:** Console shows "Provider not found: emf"
**Fix:** 
1. Clear session storage
2. Refresh the page
3. Check that bootstrap config returns provider with ID "emf"

### Issue 4: Token Exchange Failed
**Symptom:** After redirect from Authentik, token exchange fails
**Fix:**
1. Check that client ID matches: `CHf8Ic1pJpmOp7FjuZJTG8F75AJMc5xmOPVcxPDG`
2. Verify Authentik application is set to "Public" client type
3. Check that PKCE is enabled in Authentik

### Issue 5: JWT Validation Failed
**Symptom:** Token is received but backend rejects it
**Fix:**
1. Verify backend issuer URI matches: `https://authentik.rzware.com/application/o/emf`
2. Check backend logs for JWT validation errors
3. Verify JWKS endpoint is accessible: `https://authentik.rzware.com/application/o/emf/jwks/`

## Step-by-Step Debug Process

1. **Clear everything:**
   - Clear browser session storage
   - Clear browser cache (or use incognito)
   - Restart backend

2. **Open DevTools before loading page:**
   - Open Network tab
   - Open Console tab
   - Load `http://localhost:5174/`

3. **Watch the sequence:**
   - Bootstrap config loads → Check response
   - Discovery document loads → Check response
   - Click login → Watch redirect to Authentik
   - Login at Authentik → Watch redirect back
   - Token exchange → Check for errors

4. **Copy and share:**
   - Any console errors (red text)
   - Failed network requests (red in Network tab)
   - The full URL when you get redirected back from Authentik

## Quick Test Commands

**Test bootstrap endpoint:**
```bash
curl http://localhost:8080/ui/config/bootstrap | jq .
```

**Test Authentik discovery:**
```bash
curl https://authentik.rzware.com/application/o/emf/.well-known/openid-configuration | jq .
```

**Test JWKS endpoint:**
```bash
curl https://authentik.rzware.com/application/o/emf/jwks/ | jq .
```

## What to Share for Help

If still stuck, share:
1. Console errors (screenshot or copy/paste)
2. Failed network requests (URL, status, response)
3. The URL you see after Authentik redirects back
4. Backend logs (any errors related to JWT or OIDC)
