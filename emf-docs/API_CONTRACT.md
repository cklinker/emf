# EMF Control Plane API Contract

## Overview

This document defines the API contract between the EMF Control Plane and its clients (UI, SDK, CLI).

**Single Source of Truth**: `emf-control-plane/app/src/main/resources/openapi/control-plane-api.yaml`

## API Versioning

- Current Version: `v1.0.0`
- Base Path: `/control/*` for control plane operations, `/api/*` for runtime operations
- All breaking changes require a major version bump

## Endpoint Structure

### Control Plane Endpoints (`/control/*`)

These endpoints manage the platform configuration and require ADMIN role authorization:

- `/control/collections` - Collection CRUD operations
- `/control/collections/{id}/fields` - Field management
- `/control/roles` - Role management
- `/control/policies` - Policy management
- `/control/collections/{id}/authz` - Authorization configuration
- `/control/oidc/providers` - OIDC provider management
- `/control/packages` - Package import/export
- `/control/migrations` - Schema migrations

### Runtime Endpoints (`/api/*`)

These endpoints are used by applications at runtime:

- `/api/_meta/resources` - Resource discovery
- `/api/collections/{name}` - Dynamic collection data access

### UI Configuration Endpoints (`/ui/*`)

These endpoints provide UI configuration:

- `/ui/config/bootstrap` - Bootstrap configuration
- `/ui/pages` - Page management
- `/ui/menus` - Menu management

## Data Type Mappings

### Collection

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | string (UUID) | Yes | Unique identifier |
| name | string | Yes | Collection name (1-100 chars) |
| displayName | string | Yes | Display name (1-100 chars) |
| description | string | No | Description (max 500 chars) |
| storageMode | enum | Yes | PHYSICAL_TABLE or JSONB |
| active | boolean | Yes | Active status |
| currentVersion | integer | Yes | Current version number |
| fields | FieldDto[] | No | Array of fields |
| authz | AuthorizationConfigDto | No | Authorization config |
| createdAt | datetime | Yes | Creation timestamp |
| updatedAt | datetime | Yes | Last update timestamp |

### Field

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | string (UUID) | Yes | Unique identifier |
| collectionId | string (UUID) | No | Parent collection ID |
| name | string | Yes | Field name (1-100 chars) |
| displayName | string | No | Display name (max 100 chars) |
| type | enum | Yes | string, number, boolean, date, datetime, json, reference |
| required | boolean | Yes | Required field flag |
| unique | boolean | Yes | Unique constraint flag |
| indexed | boolean | Yes | Indexed flag |
| defaultValue | string (JSON) | No | Default value as JSON string |
| referenceTarget | string | No | Target collection for references |
| order | integer | Yes | Display order |
| active | boolean | Yes | Active status |
| description | string | No | Description (max 500 chars) |
| constraints | string (JSON) | No | Validation constraints as JSON |
| createdAt | datetime | Yes | Creation timestamp |
| updatedAt | datetime | Yes | Last update timestamp |

### Role

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | string (UUID) | Yes | Unique identifier |
| name | string | Yes | Role name (1-100 chars) |
| description | string | No | Description (max 500 chars) |
| createdAt | datetime | Yes | Creation timestamp |

### Policy

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | string (UUID) | Yes | Unique identifier |
| name | string | Yes | Policy name (1-100 chars) |
| description | string | No | Description (max 500 chars) |
| expression | string | No | Policy expression for evaluation |
| rules | string (JSON) | No | Policy rules as JSON string |
| createdAt | datetime | Yes | Creation timestamp |

## JSON String Fields

Several fields store structured data as JSON strings for flexibility:

- `Field.defaultValue` - Stores default value in JSON format
- `Field.constraints` - Stores validation rules as JSON
- `Policy.rules` - Stores policy rules as JSON
- `OidcProvider.scopes` - Stores scopes array as JSON

**Important**: These fields should be parsed on the client side to access structured data.

## Authentication & Authorization

- **Authentication**: Bearer JWT token in `Authorization` header
- **Authorization**: Most write operations require `ADMIN` role
- **Token Format**: `Authorization: Bearer <jwt-token>`

## Error Responses

All error responses follow this structure:

```json
{
  "timestamp": "2026-01-26T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/control/collections",
  "validationErrors": {
    "name": "Collection name is required"
  }
}
```

### HTTP Status Codes

- `200 OK` - Successful GET/PUT request
- `201 Created` - Successful POST request
- `204 No Content` - Successful DELETE request
- `400 Bad Request` - Validation errors
- `401 Unauthorized` - Missing or invalid JWT token
- `403 Forbidden` - Insufficient permissions (requires ADMIN role)
- `404 Not Found` - Resource not found
- `409 Conflict` - Resource with same name already exists
- `500 Internal Server Error` - Server error

## Versioning Strategy

Collections and fields use versioning to track schema changes:

1. Each collection has a `currentVersion` field
2. Updates to collections or fields create a new `CollectionVersion` record
3. Previous versions are preserved for rollback and audit
4. Version numbers increment automatically

## Soft Deletes

Collections and fields use soft deletes:

- DELETE operations set `active = false`
- Records remain in database for audit trail
- Inactive records are excluded from list operations by default

## Pagination

List endpoints support pagination with these query parameters:

- `page` - Page number (0-indexed, default: 0)
- `size` - Page size (default: 20)
- `sort` - Sort criteria (e.g., `name,asc` or `createdAt,desc`)

Response format:

```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

## Type Generation

TypeScript types should be generated from the OpenAPI specification:

```bash
# In emf-web directory
npm run generate-types
```

This ensures type safety and prevents drift between backend and frontend.

## Breaking Change Policy

Breaking changes require:

1. Major version bump in OpenAPI spec
2. Coordinated release across all repositories
3. Migration guide in release notes
4. Deprecation period for removed endpoints (minimum 1 release cycle)

## Testing Contract Compliance

Each repository should include contract tests:

- **Control Plane**: OpenAPI validation tests
- **SDK**: Integration tests against OpenAPI spec
- **UI**: API mock tests using OpenAPI spec

## Change Process

When modifying the API:

1. Update OpenAPI spec first (`control-plane-api.yaml`)
2. Generate TypeScript types from spec
3. Update Java DTOs to match spec
4. Update database migrations if needed
5. Update SDK client methods
6. Update UI components
7. Run contract tests across all repos
8. Update this documentation

## Repository Coordination

Changes affecting multiple repositories:

1. **emf-control-plane**: Update DTOs, controllers, OpenAPI spec
2. **emf-web**: Regenerate types, update SDK client
3. **emf-ui**: Update components to use new types
4. **emf-docs**: Update API documentation

All changes should be released together with matching version numbers.
