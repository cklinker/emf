# Kelta Auth API

## OAuth2 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/oauth2/authorize` | Authorization endpoint |
| POST | `/oauth2/token` | Token endpoint |
| POST | `/oauth2/revoke` | Token revocation |
| GET | `/oauth2/jwks` | JSON Web Key Set |
| GET | `/.well-known/openid-configuration` | OIDC discovery |
| GET | `/userinfo` | User info endpoint |

## Identity Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/users` | Create user |
| GET | `/api/v1/users/:id` | Get user |
| PATCH | `/api/v1/users/:id` | Update user |

## MFA Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/mfa/enroll` | Start MFA enrollment |
| POST | `/api/v1/mfa/verify` | Verify TOTP code |

## Health

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Application info |
