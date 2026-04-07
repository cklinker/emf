# Security Policy

For general security vulnerability reporting, see [../SECURITY.md](../SECURITY.md).

## Gateway-Specific Security Notes

### Rate Limiting

The gateway enforces per-tenant and per-IP rate limits via Redis-backed Spring Cloud Gateway filters. Rate limit thresholds are configurable via application properties. Exceeding limits returns `429 Too Many Requests`.

- Ensure Redis is secured and not publicly exposed.
- Rate limit keys are tenant-scoped to prevent cross-tenant interference.

### Auth Token Validation

All inbound requests (except `/actuator/health`) require a valid JWT issued by the configured OIDC provider (`kelta-auth`).

- JWTs are validated against the JWKS endpoint of the issuer URI.
- Token expiry and signature are enforced; expired tokens return `401 Unauthorized`.
- Authorization decisions are delegated to Cerbos via gRPC after JWT validation.
- Service-to-service calls use OAuth2 client credentials flow; client secrets must not be committed.

### Recommendations

- Never expose the gateway's actuator endpoints (`/actuator/**`) to the public internet. Restrict via network policy or Spring Security config.
- Rotate Redis passwords and OAuth2 client secrets regularly.
- Review Cerbos policies when adding new routes to ensure authorization rules are applied.
