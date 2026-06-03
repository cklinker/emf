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

### JSON:API Error Responses (4xx and 5xx)

Every error response — including ones written directly from a servlet filter or
returned by a controller — MUST follow the JSON:API error shape and populate at
minimum `status`, `code`, `title`, and `detail`. Empty error objects
(`{"errors":[{}]}`) are a regression: clients cannot distinguish wrong path
from wrong payload from auth failure without trial-and-error.

```json
{
  "errors": [{
    "status": "400",
    "code": "INVALID_PAYLOAD",
    "title": "Bad Request",
    "detail": "Human-readable description of what went wrong",
    "source": { "pointer": "/data/attributes/email" }
  }]
}
```

Field semantics:
- `status` — HTTP status code as a string (`"400"`, `"404"`, `"422"`)
- `code` — stable, machine-readable identifier in `UPPER_SNAKE_CASE`
  (`NOT_FOUND`, `INVALID_PAYLOAD`, `UNAUTHORIZED`, `VALIDATION_FAILED`,
  `UNIQUE_CONSTRAINT_VIOLATION`). Clients branch on this; it must not change
  between releases for the same error condition.
- `title` — short, stable, human-readable summary suitable for a UI headline
  (`"Bad Request"`, `"Validation Error"`, `"Not Found"`). Distinct from `code`.
- `detail` — specific message for this occurrence. Free-form, may include the
  offending value when safe.
- `source.pointer` — JSON Pointer to the offending field on field-level errors
  (`/data/attributes/email`). Omit for non-field errors.
- `source.parameter` — query / path parameter name for parameter-level errors.
- `meta.requestId` / `meta.correlationId` — populated by the global handler for
  trace correlation; controllers/filters do not need to set them.

How to build error bodies:
- Prefer the global exception handler: throw a typed exception
  (`ValidationException`, `InvalidQueryException`, `UniqueConstraintViolationException`)
  and let `GlobalExceptionHandler` produce the response. Common Spring
  exceptions (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`,
  `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException`,
  `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`,
  `NoResourceFoundException`) are already mapped — do not reinvent them.
- When a controller or filter must return an error body directly, use
  `JsonApiResponseBuilder.error(status, code, title, detail, pointer)` from
  `io.kelta.jsonapi`. The four-arg overload (no pointer) is fine when there is
  no specific field.
- For inline-JSON filters (servlet `Filter` + `response.getWriter().write(...)`),
  hand-write the JSON but include all four required fields. Never emit
  `{"error": "..."}` (singular) — that does not match the JSON:API envelope.

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
