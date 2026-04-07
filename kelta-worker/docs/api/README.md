# kelta-worker API Reference

All endpoints are served on the worker's internal port (default `8080`). The gateway proxies collection endpoints externally; internal endpoints are cluster-only.

## Collection Endpoints (JSON:API)

Dynamic routes registered by `DynamicCollectionRouter` for every active collection. All paths follow the JSON:API 1.1 specification.

### Records

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/{tenant}/{collection}` | List records (filterable, sortable, pageable) |
| `POST` | `/api/{tenant}/{collection}` | Create a record |
| `GET` | `/api/{tenant}/{collection}/{id}` | Fetch a single record |
| `PATCH` | `/api/{tenant}/{collection}/{id}` | Update a record |
| `DELETE` | `/api/{tenant}/{collection}/{id}` | Delete a record |

### Relationships

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/{tenant}/{collection}/{id}/relationships/{rel}` | Fetch relationship data |
| `PATCH` | `/api/{tenant}/{collection}/{id}/relationships/{rel}` | Update a to-one relationship |
| `POST` | `/api/{tenant}/{collection}/{id}/relationships/{rel}` | Add to a to-many relationship |
| `DELETE` | `/api/{tenant}/{collection}/{id}/relationships/{rel}` | Remove from a to-many relationship |

### Query Parameters

| Parameter | Description |
|-----------|-------------|
| `filter[{field}]` | Filter by field value |
| `sort` | Comma-separated field names; prefix with `-` for descending |
| `page[number]` | Page number (1-based) |
| `page[size]` | Page size (default 20, max 200) |
| `include` | Comma-separated relationship names to include |
| `fields[{type}]` | Sparse fieldsets — comma-separated field names |

## Workflow Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/{tenant}/workflows/{ruleId}/trigger` | Manually trigger a workflow rule |

## Webhook Endpoints (Svix portal)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/{tenant}/webhooks/portal` | Get Svix consumer portal URL |
| `POST` | `/api/{tenant}/webhooks/endpoints` | Register a webhook endpoint |
| `GET` | `/api/{tenant}/webhooks/endpoints` | List registered webhook endpoints |
| `DELETE` | `/api/{tenant}/webhooks/endpoints/{endpointId}` | Remove a webhook endpoint |

## Internal Endpoints (gateway-facing only)

These endpoints are called by `kelta-gateway` during bootstrap and request processing. They are not exposed externally.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/internal/bootstrap` | All active collections + tenant governor limits |
| `GET` | `/internal/tenants/slug-map` | Map of tenant slug to tenant UUID |
| `GET` | `/internal/oidc/by-issuer` | OIDC provider config lookup by issuer URI |
| `GET` | `/internal/permissions` | Effective permissions for a user (profiles + permission sets + groups) |

### `/internal/bootstrap` Response Shape

```json
{
  "collections": [
    {
      "id": "uuid",
      "slug": "orders",
      "tenantId": "uuid",
      "fields": [...],
      "permissions": {...}
    }
  ],
  "governorLimits": {
    "tenantId": "uuid",
    "apiCallsPerDay": 100000,
    "storageLimitBytes": 10737418240,
    "maxUsers": 100,
    "maxCollections": 200
  }
}
```

## Actuator Endpoints

Spring Boot Actuator is enabled. Available at `/actuator/`:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Liveness and readiness checks |
| `/actuator/prometheus` | Prometheus metrics scrape endpoint |
| `/actuator/info` | Application version and build info |

## Governor Limits Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/internal/governor/limits/{tenantId}` | Fetch current limits for a tenant |
| `GET` | `/internal/governor/usage/{tenantId}` | Fetch current usage counters for a tenant |

## Error Responses

All errors follow JSON:API error format:

```json
{
  "errors": [
    {
      "status": "422",
      "title": "Unprocessable Entity",
      "detail": "Field 'email' is required",
      "source": {
        "pointer": "/data/attributes/email"
      }
    }
  ]
}
```

Common HTTP status codes:
- `400` — Bad request (malformed JSON:API document)
- `401` — Unauthenticated
- `403` — Unauthorized (Cerbos denied)
- `404` — Record or collection not found
- `409` — Conflict (optimistic lock failure)
- `422` — Validation error
- `429` — Governor limit exceeded
- `500` — Internal server error
