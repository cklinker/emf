# Coding Conventions

## Java

### Naming
- **Classes**: PascalCase (`RouteDefinition`, `CollectionLifecycleManager`)
- **Methods**: camelCase (`getId()`, `refreshCollectionDefinition()`)
- **Constants**: UPPER_SNAKE_CASE (`TAG_TENANT`, `TAG_METHOD`)
- **Fields**: camelCase with `private final` (`private final MeterRegistry registry`)
- **Booleans**: `is`/`has` prefix for getters (`isActive()`, `hasRateLimit()`)
- **Packages**: `io.kelta.<service>.<feature>` (e.g., `io.kelta.authz.cerbos`)

### Style
- 4-space indentation, 1TBS braces
- No wildcard imports; sorted alphabetically within groups
- Import order: (1) `io.kelta.*`, (2) external libs (`com.fasterxml.*`, `org.*`), (3) Java stdlib (`java.*`, `javax.*`)
- Constructor injection preferred over field injection
- `@Component` for beans, `@Service` for business logic, `@Repository` for data access
- `@KafkaListener` with explicit topics and group IDs

### Logging
- `private static final Logger logger = LoggerFactory.getLogger(ClassName.class)`
- Parameterized logging (not string concat): `logger.info("Processing: id={}, name={}", id, name)`
- JSON structured output via Logstash Logback Encoder

### Error Handling
- Try/catch with full exception logging: `logger.error("Error: {}", e.getMessage(), e)`
- Custom exceptions extend from base types
- No silent failures, graceful degradation on non-critical failures

#### JSON:API error response shape (4xx)

All 4xx responses returned from any Kelta service MUST use the JSON:API error envelope. Every object in the `errors[]` array carries at minimum `status`, `code`, `detail`, and (for field-level errors) `source.pointer`:

```json
{
  "errors": [
    {
      "status": "400",
      "code": "VALIDATION_FAILED",
      "title": "Validation Error",
      "detail": "name must not be blank",
      "source": { "pointer": "/data/attributes/name" },
      "meta": { "requestId": "abc12345" }
    }
  ]
}
```

- `status` — HTTP status code as a string (`"400"`, `"404"`, …)
- `code` — stable, machine-readable identifier in `UPPER_SNAKE_CASE` (`NOT_FOUND`, `INVALID_PAYLOAD`, `VALIDATION_FAILED`, `UNAUTHORIZED`, `RATE_LIMIT_EXCEEDED`, …). Clients branch on `code`, not `detail`.
- `title` — short, human-readable category (`"Bad Request"`, `"Not Found"`, …)
- `detail` — human-readable description of *this* failure. Never `null`, never empty.
- `source.pointer` — JSON Pointer (RFC 6901) to the offending field on a request body — e.g. `/data/attributes/email`. For query / path parameters use `source.parameter` with the parameter name instead.

Never emit an empty error object (`{}`). If you reach a path with no specific information, fall through to the generic handler so clients still get a populated envelope.

Where errors are constructed:
- `kelta-gateway/src/main/java/io/kelta/gateway/error/GlobalErrorHandler.java` — reactive (`ErrorWebExceptionHandler`) for all gateway-originating 4xx/5xx
- `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/runtime/router/GlobalExceptionHandler.java` — servlet (`@ControllerAdvice`) covering bean validation, malformed bodies, missing params, type mismatches, `NoResourceFoundException`/`NoHandlerFoundException`, `MethodNotAllowed`, `UnsupportedMediaType`, `ResponseStatusException`, plus the platform's own `ValidationException` / `InvalidQueryException` / `UniqueConstraintViolationException`
- `io.kelta.jsonapi.JsonApiResponseBuilder.error(...)` — utility for one-off error documents in controllers; the 3-arg overload derives `code` from `title` so existing callers stay compliant
- `io.kelta.jsonapi.JsonApiError` — POJO used by the handlers; `@JsonInclude(NON_NULL)` keeps absent fields out of the wire format

### Javadoc
- Required for public classes and methods
- Include `@param`, `@returns`, `@throws`

## TypeScript

### Naming
- **Files**: PascalCase for components (`FilterBuilder.tsx`), camelCase for modules in SDK
- **Interfaces**: PascalCase, no `I` prefix (`TokenState`, `TokenProvider`)
- **Functions**: camelCase (`parseTokenExpiration()`)
- **Enums**: PascalCase name, UPPER_SNAKE_CASE values

### Style (kelta-web)
- 2-space indent, single quotes, semicolons required, trailing commas (ES5), 100 char width
- **Note**: `kelta-ui/app/.prettierrc` omits semicolons (`"semi": false`)

### ESLint Rules (kelta-web)
- `@typescript-eslint/no-explicit-any`: warn
- `@typescript-eslint/no-floating-promises`: error
- `@typescript-eslint/no-unused-vars`: error (ignore `_` prefix)
- `react-hooks/rules-of-hooks`: error
- `no-console`: warn (allow warn/error)
- `prefer-const`: error, `no-var`: error

### Imports
- Order: (1) type imports, (2) internal (`@kelta/sdk`, `@kelta/components`), (3) relative, (4) external
- Named exports preferred, barrel files for public API, default exports only for React components

### Error Handling
- Custom hierarchy: `KeltaError extends Error` with `statusCode`, `details`
- `mapAxiosError()` for HTTP error translation
- Zod schemas for runtime validation

### Type Safety
- Generics for type-safe resource ops: `ResourceClient<T>`
- Explicit return types on public methods
- Readonly properties for immutability

### Component reuse (kelta-ui/app and kelta-web)

- Do not add a new shared list/table/filter/form component in `kelta-ui/app/src/components/` if one already exists for the same purpose in either `kelta-ui/app/` or `kelta-web/packages/components/`. Reuse or extend the existing one.
- The unification target for these families (DataTable, FilterBuilder, FieldRenderer, ResourceForm, RelatedList) is the library variant under `@kelta/components`. App-side variants are being collapsed into thin re-exports — see `.claude/docs/ui-consolidation-plan.md` for the current state and migration order.
- `@kelta/components` is a public plugin surface. Breaking changes to its exported props need a deprecation window (additive props, `legacy*` flags) — never a hard cutover.

## Schema field naming (JSON:API reserved words)

The platform serializes records as JSON:API resource objects, so a handful of attribute names collide with the document envelope and **cannot be used as schema field names**. Field-create requests that use a reserved name are rejected with HTTP 400 by `FieldLifecycleHook.beforeCreate` (`runtime-module-schema`).

### Reserved field names

The following names are reserved at the JSON:API document level and MUST NOT be used as schema field names:

- `type` — collides with the JSON:API resource type discriminator (`data.type`)
- `id` — collides with the JSON:API resource identifier (`data.id`)
- `attributes` — collides with the JSON:API attributes container (`data.attributes`)
- `relationships` — collides with the JSON:API relationships container (`data.relationships`)
- `links` — reserved by JSON:API for hypermedia links (`data.links`)
- `meta` — reserved by JSON:API for non-standard metadata (`data.meta`)

In addition, the platform reserves these Kelta-internal column names that `BaseEntity` and tenancy infrastructure already populate:

- `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `tenantId`

The authoritative list lives in `FieldLifecycleHook.RESERVED_FIELD_NAMES` (`kelta-platform/runtime/runtime-module-schema/src/main/java/io/kelta/runtime/module/schema/hooks/FieldLifecycleHook.java`). Update both that constant and this document when expanding the list.

### Workaround: prefix with the entity name

When a schema needs a field whose natural name is reserved (most commonly `type` to discriminate variants of a record), prefix the field with the entity name in camelCase:

| Entity   | Reserved name | Use instead    |
|----------|---------------|----------------|
| `Title`  | `type`        | `titleType`    |
| `Alert`  | `type`        | `alertType`    |
| `Asset`  | `type`        | `assetType`    |
| `Event`  | `id`          | `eventId`      |
| `Page`   | `links`       | `pageLinks`    |

The entity prefix is preferred over generic alternatives (`kind`, `category`, `variant`) because it reads cleanly in JSON:API responses (`attributes.titleType: "h1"`) and avoids ambiguity when records are joined or denormalized.

### Why we don't auto-mangle

We considered transparently renaming reserved fields on storage (e.g. accepting `type` on the wire and persisting it as `_type`, or namespacing every user attribute under a dummy key) and rejected it for these reasons:

1. **Round-trip surprise.** A field named `type` in the schema builder UI would not match what appears in API responses, breaking client code that consults the schema to drive serialization (the SDK's `ResourceClient<T>` and the JSON:API parser both walk attributes by exact name).
2. **Search, filter, sort.** `filter[type]=foo` and `sort=type` would need to be silently rewritten in both the gateway query parser and the worker storage layer; the gap between the wire contract and the persisted column is exactly where bugs hide.
3. **Plugin contract.** `@kelta/components` and the public plugin SDK expose field names directly to third-party code. A mangling layer would become a forever-compat surface.
4. **One workaround is enough.** Prefixing with the entity name takes seconds at schema-authoring time and produces clearer JSON for every downstream consumer.

**Decision:** reserved names stay rejected at field-create time. Schema authors are expected to prefix. This document is the canonical reference for that rule.
