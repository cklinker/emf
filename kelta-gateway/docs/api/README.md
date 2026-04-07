# kelta-gateway API Reference

The gateway does not expose application-level APIs directly. It proxies requests to upstream services. The endpoints below are gateway-owned.

## Health and Actuator

| Method | Path | Auth Required | Description |
|--------|------|---------------|-------------|
| `GET` | `/actuator/health` | No | Liveness and readiness probe |
| `GET` | `/actuator/health/liveness` | No | Liveness probe only |
| `GET` | `/actuator/health/readiness` | No | Readiness probe only |
| `GET` | `/actuator/info` | No | Build and version info |
| `GET` | `/actuator/prometheus` | Network-restricted | Prometheus metrics scrape endpoint |

> Actuator endpoints other than health are restricted by network policy and must not be exposed publicly.

## Routing

The gateway proxies the following path prefixes to upstream services. All proxied paths require a valid Bearer JWT.

| Path Prefix | Upstream Service | Notes |
|-------------|-----------------|-------|
| `/api/**` | `kelta-worker` | Main platform API (objects, workflows, collections) |
| `/auth/**` | `kelta-auth` | OIDC token endpoints, identity management |
| `/ai/**` | `kelta-ai` | AI assistant endpoints |

### Injected Headers

For all forwarded requests, the gateway injects:

| Header | Value |
|--------|-------|
| `X-Tenant-ID` | Tenant ID resolved from JWT `tenant_id` claim |
| `X-Principal-ID` | User/subject ID from JWT `sub` claim |
| `X-Request-ID` | Unique request trace ID (generated or forwarded if present) |

## Rate Limiting

Rate limit responses return `429 Too Many Requests` with the following headers:

| Header | Description |
|--------|-------------|
| `X-RateLimit-Limit` | Maximum requests allowed in the window |
| `X-RateLimit-Remaining` | Requests remaining in the current window |
| `X-RateLimit-Reset` | Unix timestamp when the window resets |

## Error Responses

| Status | Condition |
|--------|-----------|
| `401 Unauthorized` | Missing, expired, or invalid JWT |
| `403 Forbidden` | Valid JWT but Cerbos policy denied the request |
| `429 Too Many Requests` | Rate limit exceeded |
| `502 Bad Gateway` | Upstream service unreachable |
| `504 Gateway Timeout` | Upstream service did not respond in time |
