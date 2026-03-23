# Research: JSON:API Atomic Operations Extension

**Date:** 2026-03-22
**Type:** Web
**Purpose:** Inform Phase 3 batch operations implementation

## Summary

The Atomic Operations extension (`https://jsonapi.org/ext/atomic`) is the **only official JSON:API extension**. It allows multiple write operations (create, update, delete) in a single HTTP request with **all-or-nothing transaction semantics**.

## Key Facts

- **Endpoint:** Single POST to `/operations` (or `/api/operations`)
- **Content-Type:** `application/vnd.api+json;ext="https://jsonapi.org/ext/atomic"`
- **Operations:** `add` (create), `update` (modify), `remove` (delete)
- **Transaction:** All-or-nothing — any failure rolls back all preceding operations
- **Sequential:** Operations processed in array order
- **Local IDs (`lid`):** Allow referencing resources created earlier in same request

## Request Format

```json
{
  "atomic:operations": [
    { "op": "add", "data": { "type": "contacts", "lid": "temp-1", "attributes": { "name": "Alice" } } },
    { "op": "update", "ref": { "type": "contacts", "id": "123" }, "data": { "type": "contacts", "id": "123", "attributes": { "name": "Updated" } } },
    { "op": "remove", "ref": { "type": "contacts", "id": "456" } }
  ]
}
```

## Response Format

```json
{
  "atomic:results": [
    { "data": { "type": "contacts", "id": "server-1", "lid": "temp-1", "attributes": { "name": "Alice" } } },
    { "data": { "type": "contacts", "id": "123", "attributes": { "name": "Updated" } } },
    {}
  ]
}
```

## Error Handling

Errors include `source.pointer` referencing the failing operation by index:
```json
{ "errors": [{ "status": "422", "source": { "pointer": "/atomic:operations/2/data/attributes/title" } }] }
```

## Implementation Decisions for Kelta

| Decision | Recommendation |
|----------|---------------|
| Max operations/request | Default 100, configurable per-tenant, hard ceiling 500 |
| Transaction boundary | Database transaction wrapping all operations |
| Validation strategy | Single-pass: validate + execute sequentially, rollback on first failure |
| Authorization | Each operation individually authorized; fail batch if any unauthorized |
| SQL optimization | JDBC batch inserts/updates within the transaction |
| lid map | Maintain `lid -> server-generated-id` map during request processing |

## Reference Implementations

- **JsonApiDotNetCore** (C#): Default 10 ops, configurable. Mature.
- **Elide** (Java): Requires client-generated UUIDs. Full transaction support.
- **FastAPI-JSONAPI** (Python): SQLAlchemy-backed. No `href` support.

---

*Research completed: 2026-03-22*
*Sources: jsonapi.org/ext/atomic/, jsonapi.net, elide.io*
