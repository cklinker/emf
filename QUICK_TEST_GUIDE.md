# Quick Test Guide - OIDC Claim Mapping

## Quick Start

### 1. Start Backend
```bash
cd emf-control-plane
mvn spring-boot:run -pl app
```

### 2. Start Frontend
```bash
cd emf-ui/app
npm run dev
```

### 3. Access UI
Open browser: http://localhost:5173

## Quick API Tests

### Create Provider with Claim Mappings
```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Provider",
    "issuer": "https://example.com",
    "jwksUri": "https://example.com/.well-known/jwks.json",
    "clientId": "test-client",
    "rolesClaim": "realm_access.roles",
    "rolesMapping": "{\"admin\": \"ADMIN\", \"user\": \"USER\"}",
    "emailClaim": "email",
    "usernameClaim": "preferred_username",
    "nameClaim": "name"
  }'
```

### List Providers
```bash
curl http://localhost:8080/control/oidc/providers
```

### Update Provider
```bash
# Replace <id> with actual provider ID
curl -X PUT http://localhost:8080/control/oidc/providers/<id> \
  -H "Content-Type: application/json" \
  -d '{
    "rolesClaim": "groups",
    "emailClaim": "mail"
  }'
```

## Quick UI Tests

1. **Navigate to OIDC Providers**
   - Click "OIDC Providers" in sidebar
   - Click "Add Provider" button

2. **Fill Form with Claim Mappings**
   - Name: "Test Provider"
   - Issuer: "https://example.com"
   - JWKS URI: "https://example.com/.well-known/jwks.json"
   - Client ID: "test-client"
   - Roles Claim: "realm_access.roles"
   - Roles Mapping: `{"admin": "ADMIN", "user": "USER"}`
   - Email Claim: "email"
   - Username Claim: "preferred_username"
   - Name Claim: "name"

3. **Test Validation**
   - Enter invalid JSON in Roles Mapping: `{invalid`
   - Should see error: "Invalid JSON format"
   - Enter long claim path (>200 chars)
   - Should see error: "Claim path too long"

4. **Edit Existing Provider**
   - Click edit on a provider
   - Verify claim fields are populated
   - Change values and save
   - Verify changes persist

## Expected Results

✅ **Backend:**
- All 79 claim mapping tests pass
- API accepts and returns claim fields
- Validation errors return 400 with descriptive messages

✅ **Frontend:**
- Form displays all claim fields
- Validation works correctly
- Data persists after save
- Existing providers load correctly

## Common OIDC Provider Examples

### Keycloak
```json
{
  "rolesClaim": "realm_access.roles",
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name"
}
```

### Auth0
```json
{
  "rolesClaim": "https://your-app.com/roles",
  "emailClaim": "email",
  "usernameClaim": "nickname",
  "nameClaim": "name"
}
```

### Okta
```json
{
  "rolesClaim": "groups",
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name"
}
```

## Troubleshooting

**Backend won't start:**
- Check PostgreSQL is running
- Check port 8080 is available

**Frontend won't start:**
- Run `npm install` first
- Check port 5173 is available

**Can't see providers in UI:**
- Check backend is running
- Check browser console for errors
- Verify API endpoint is accessible

**Validation not working:**
- Check browser console for errors
- Verify form fields have correct test IDs
- Check translation keys are loaded

## Success Criteria

- [x] Backend tests pass (79/79)
- [ ] Can create provider with claim mappings via UI
- [ ] Can edit provider claim mappings via UI
- [ ] Validation errors display correctly
- [ ] Existing providers load with defaults
- [ ] Package export includes claim fields

---

**For detailed testing:** See `emf-control-plane/MANUAL_TESTING_GUIDE.md`  
**For full results:** See `TASK_16_E2E_TESTING_SUMMARY.md`
