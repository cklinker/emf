# Manual Testing Guide - OIDC Claim Mapping

This guide provides step-by-step instructions for manually testing the OIDC claim mapping feature using curl.

## Prerequisites

1. **Start the local development environment:**
   ```bash
   # From the workspace root
   docker-compose up -d postgres redis kafka
   ```

2. **Run the control plane application:**
   ```bash
   cd emf-control-plane
   mvn spring-boot:run -pl app
   ```

3. **Wait for the application to start** (check logs for "Started ControlPlaneApplication")

## Test Scenarios

### 1. Create OIDC Provider with Claim Mappings

**Test creating a new OIDC provider with all claim mapping fields:**

```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Keycloak Dev",
    "issuer": "http://localhost:8180/realms/emf",
    "jwksUri": "http://localhost:8180/realms/emf/protocol/openid-connect/certs",
    "clientId": "emf-control-plane",
    "audience": "emf-api",
    "rolesClaim": "realm_access.roles",
    "rolesMapping": "{\"keycloak-admin\": \"ADMIN\", \"keycloak-user\": \"USER\"}",
    "emailClaim": "email",
    "usernameClaim": "preferred_username",
    "nameClaim": "name"
  }'
```

**Expected Response:**
```json
{
  "id": "<generated-uuid>",
  "name": "Keycloak Dev",
  "issuer": "http://localhost:8180/realms/emf",
  "jwksUri": "http://localhost:8180/realms/emf/protocol/openid-connect/certs",
  "clientId": "emf-control-plane",
  "audience": "emf-api",
  "rolesClaim": "realm_access.roles",
  "rolesMapping": "{\"keycloak-admin\": \"ADMIN\", \"keycloak-user\": \"USER\"}",
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name",
  "active": true,
  "createdAt": "2026-02-01T...",
  "updatedAt": "2026-02-01T..."
}
```

### 2. Create OIDC Provider with Default Values

**Test that default values are applied when claim fields are omitted:**

```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Auth0 Dev",
    "issuer": "https://dev-example.auth0.com",
    "jwksUri": "https://dev-example.auth0.com/.well-known/jwks.json",
    "clientId": "auth0-client-id"
  }'
```

**Expected Response:**
```json
{
  "id": "<generated-uuid>",
  "name": "Auth0 Dev",
  "issuer": "https://dev-example.auth0.com",
  "jwksUri": "https://dev-example.auth0.com/.well-known/jwks.json",
  "clientId": "auth0-client-id",
  "audience": null,
  "rolesClaim": null,
  "rolesMapping": null,
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name",
  "active": true,
  "createdAt": "2026-02-01T...",
  "updatedAt": "2026-02-01T..."
}
```

### 3. List All OIDC Providers

**Test retrieving all providers with claim mappings:**

```bash
curl -X GET http://localhost:8080/control/oidc/providers
```

**Expected Response:**
```json
[
  {
    "id": "<uuid-1>",
    "name": "Keycloak Dev",
    "rolesClaim": "realm_access.roles",
    "rolesMapping": "{\"keycloak-admin\": \"ADMIN\", \"keycloak-user\": \"USER\"}",
    "emailClaim": "email",
    "usernameClaim": "preferred_username",
    "nameClaim": "name",
    ...
  },
  {
    "id": "<uuid-2>",
    "name": "Auth0 Dev",
    "rolesClaim": null,
    "rolesMapping": null,
    "emailClaim": "email",
    "usernameClaim": "preferred_username",
    "nameClaim": "name",
    ...
  }
]
```

### 4. Update OIDC Provider Claim Mappings

**Test updating claim mappings for an existing provider:**

```bash
# Replace <provider-id> with actual ID from previous responses
curl -X PUT http://localhost:8080/control/oidc/providers/<provider-id> \
  -H "Content-Type: application/json" \
  -d '{
    "rolesClaim": "groups",
    "rolesMapping": "{\"admin-group\": \"ADMIN\", \"user-group\": \"USER\", \"viewer-group\": \"VIEWER\"}",
    "emailClaim": "mail",
    "usernameClaim": "sub",
    "nameClaim": "full_name"
  }'
```

**Expected Response:**
```json
{
  "id": "<provider-id>",
  "name": "Auth0 Dev",
  "rolesClaim": "groups",
  "rolesMapping": "{\"admin-group\": \"ADMIN\", \"user-group\": \"USER\", \"viewer-group\": \"VIEWER\"}",
  "emailClaim": "mail",
  "usernameClaim": "sub",
  "nameClaim": "full_name",
  "updatedAt": "2026-02-01T...",
  ...
}
```

### 5. Test Validation - Invalid JSON

**Test that invalid JSON in rolesMapping is rejected:**

```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Invalid Provider",
    "issuer": "https://example.com",
    "jwksUri": "https://example.com/.well-known/jwks.json",
    "rolesMapping": "{invalid json"
  }'
```

**Expected Response:**
```json
{
  "timestamp": "2026-02-01T...",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "rolesMapping",
      "message": "Invalid JSON format: ..."
    }
  ]
}
```

### 6. Test Validation - Claim Path Too Long

**Test that claim paths exceeding 200 characters are rejected:**

```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Invalid Provider",
    "issuer": "https://example.com",
    "jwksUri": "https://example.com/.well-known/jwks.json",
    "rolesClaim": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  }'
```

**Expected Response:**
```json
{
  "timestamp": "2026-02-01T...",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "rolesClaim",
      "message": "Claim path must not exceed 200 characters"
    }
  ]
}
```

### 7. Test Validation - Invalid Claim Path Format

**Test that claim paths with special characters are rejected:**

```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Invalid Provider",
    "issuer": "https://example.com",
    "jwksUri": "https://example.com/.well-known/jwks.json",
    "rolesClaim": "roles@admin"
  }'
```

**Expected Response:**
```json
{
  "timestamp": "2026-02-01T...",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "rolesClaim",
      "message": "Claim path must contain only letters, numbers, dots, and underscores"
    }
  ]
}
```

### 8. Test Nested Claim Paths

**Test that complex nested claim paths are accepted:**

```bash
curl -X POST http://localhost:8080/control/oidc/providers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Nested Claims Provider",
    "issuer": "https://example.com",
    "jwksUri": "https://example.com/.well-known/jwks.json",
    "rolesClaim": "resource_access.my_client.roles",
    "emailClaim": "user.contact.email",
    "usernameClaim": "user.identity.username",
    "nameClaim": "user.profile.display_name"
  }'
```

**Expected Response:**
```json
{
  "id": "<generated-uuid>",
  "name": "Nested Claims Provider",
  "rolesClaim": "resource_access.my_client.roles",
  "emailClaim": "user.contact.email",
  "usernameClaim": "user.identity.username",
  "nameClaim": "user.profile.display_name",
  ...
}
```

### 9. Test Package Export with Claim Mappings

**Test that claim mappings are included in package exports:**

```bash
curl -X POST http://localhost:8080/control/packages/export \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-export",
    "version": "1.0.0",
    "description": "Test export with OIDC claim mappings",
    "includeOidcProviders": true
  }'
```

**Expected Response:**
```json
{
  "id": "<package-id>",
  "name": "test-export",
  "version": "1.0.0",
  "description": "Test export with OIDC claim mappings",
  "oidcProviders": [
    {
      "name": "Keycloak Dev",
      "issuer": "http://localhost:8180/realms/emf",
      "jwksUri": "http://localhost:8180/realms/emf/protocol/openid-connect/certs",
      "clientId": "emf-control-plane",
      "audience": "emf-api",
      "rolesClaim": "realm_access.roles",
      "rolesMapping": "{\"keycloak-admin\": \"ADMIN\", \"keycloak-user\": \"USER\"}",
      "emailClaim": "email",
      "usernameClaim": "preferred_username",
      "nameClaim": "name"
    }
  ],
  ...
}
```

### 10. Delete OIDC Provider

**Test soft deletion of an OIDC provider:**

```bash
# Replace <provider-id> with actual ID
curl -X DELETE http://localhost:8080/control/oidc/providers/<provider-id>
```

**Expected Response:**
```
HTTP/1.1 204 No Content
```

## Common OIDC Provider Examples

### Keycloak
```json
{
  "name": "Keycloak",
  "issuer": "http://localhost:8180/realms/emf",
  "jwksUri": "http://localhost:8180/realms/emf/protocol/openid-connect/certs",
  "rolesClaim": "realm_access.roles",
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name"
}
```

### Auth0
```json
{
  "name": "Auth0",
  "issuer": "https://your-tenant.auth0.com",
  "jwksUri": "https://your-tenant.auth0.com/.well-known/jwks.json",
  "rolesClaim": "https://your-app.com/roles",
  "emailClaim": "email",
  "usernameClaim": "nickname",
  "nameClaim": "name"
}
```

### Okta
```json
{
  "name": "Okta",
  "issuer": "https://your-domain.okta.com/oauth2/default",
  "jwksUri": "https://your-domain.okta.com/oauth2/default/v1/keys",
  "rolesClaim": "groups",
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name"
}
```

### Authentik
```json
{
  "name": "Authentik",
  "issuer": "https://authentik.example.com/application/o/emf",
  "jwksUri": "https://authentik.example.com/application/o/emf/jwks",
  "rolesClaim": "groups",
  "emailClaim": "email",
  "usernameClaim": "preferred_username",
  "nameClaim": "name"
}
```

## Verification Checklist

- [ ] Create provider with all claim fields - returns 201 with all fields
- [ ] Create provider without claim fields - returns 201 with defaults
- [ ] List providers - returns all providers with claim fields
- [ ] Update provider claim fields - returns 200 with updated fields
- [ ] Invalid JSON in rolesMapping - returns 400 with error
- [ ] Claim path > 200 chars - returns 400 with error
- [ ] Invalid claim path format - returns 400 with error
- [ ] Nested claim paths - accepted successfully
- [ ] Package export - includes claim fields
- [ ] Delete provider - returns 204

## Troubleshooting

### Application won't start
- Check that PostgreSQL is running: `docker ps | grep postgres`
- Check database connection in logs
- Verify database migrations ran successfully

### Tests fail with validation errors
- Check request JSON is valid
- Verify all required fields are present (name, issuer, jwksUri)
- Check claim path format (alphanumeric, dots, underscores only)

### Can't connect to localhost:8080
- Verify application started successfully
- Check for port conflicts
- Review application logs for errors

## Next Steps

After manual testing is complete:
1. Document any issues found
2. Proceed with frontend implementation (Tasks 9-16)
3. Test end-to-end with UI once frontend is complete
