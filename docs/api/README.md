# Kelta Platform API

The Kelta Platform exposes a JSON:API compliant REST API through the API Gateway.

## Base URL

```
https://<gateway-host>/api/v1
```

## Authentication

All API requests require a valid Bearer token obtained from the kelta-auth OIDC provider.

```
Authorization: Bearer <access_token>
```

## Core Endpoints

### Collections

| Method | Path | Description |
|--------|------|-------------|
| GET | `/collections` | List all collections |
| POST | `/collections` | Create a collection |
| GET | `/collections/:id` | Get a collection |
| PATCH | `/collections/:id` | Update a collection |
| DELETE | `/collections/:id` | Delete a collection |

### Records

| Method | Path | Description |
|--------|------|-------------|
| GET | `/collections/:collectionId/records` | List records |
| POST | `/collections/:collectionId/records` | Create a record |
| GET | `/collections/:collectionId/records/:id` | Get a record |
| PATCH | `/collections/:collectionId/records/:id` | Update a record |
| DELETE | `/collections/:collectionId/records/:id` | Delete a record |

### Workflows

| Method | Path | Description |
|--------|------|-------------|
| GET | `/workflows` | List workflows |
| POST | `/workflows` | Create a workflow |
| POST | `/workflows/:id/execute` | Execute a workflow |

## Response Format

All responses follow the [JSON:API specification](https://jsonapi.org/):

```json
{
  "data": {
    "type": "collections",
    "id": "uuid",
    "attributes": { }
  }
}
```

## Error Handling

Errors are returned in JSON:API error format with appropriate HTTP status codes.

## Rate Limiting

API requests are rate-limited at the gateway layer. Rate limit headers are included in all responses.
