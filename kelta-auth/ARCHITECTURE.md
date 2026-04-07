# Kelta Auth Architecture

## Overview

Kelta Auth is the internal OIDC provider for the Kelta platform. It issues tokens consumed by kelta-gateway and kelta-worker.

## Components

### Authorization Server
Spring Authorization Server handling OAuth2 flows (authorization code, client credentials, refresh token).

### Identity Brokering
Delegates authentication to external identity providers (Google, GitHub, enterprise SAML/OIDC) and maps external identities to Kelta users.

### MFA
TOTP-based multi-factor authentication using the `dev.samstevens.totp` library. Enrollment and verification handled via dedicated endpoints.

### Session Management
HTTP sessions backed by Redis via Spring Session. Enables stateless horizontal scaling.

## Data Flow

```
Client → /oauth2/authorize → Login UI → Authentication → Token Issuance
                                ↓
                        External IdP (optional)
                                ↓
                        Identity Mapping → Kelta User
```

## Storage

- **PostgreSQL**: OAuth2 authorization data, registered clients, user accounts
- **Redis**: HTTP sessions, CSRF tokens
