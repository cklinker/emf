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

### JSON:API error envelope (all 4xx/5xx responses)

Every error response must follow JSON:API and populate `errors[].{status, code, title, detail}`. Empty `{"errors":[{}]}` payloads are a bug — clients cannot distinguish failure modes without them.

Each error object MUST include:
- `status` — HTTP status as a string (`"400"`, `"404"`, `"409"`, ...)
- `code` — machine-readable, `UPPER_SNAKE_CASE` (`INVALID_PAYLOAD`, `NOT_FOUND`, `VALIDATION_FAILED`, `MISSING_HEADER`, ...)
- `title` — short human-readable label (typically the status reason phrase)
- `detail` — human-readable description of the specific failure

Optional but recommended:
- `source.pointer` — JSON pointer to the offending attribute on field-level validation (e.g. `/data/attributes/email`)
- `source.parameter` / `source.header` — for query-parameter / header errors
- `meta.requestId` and `meta.path` — for traceability

Construction:
- Worker-side controllers: throw a runtime exception (`ValidationException`, `InvalidQueryException`, `ResponseStatusException`, ...) and let `io.kelta.runtime.router.GlobalExceptionHandler` (`@ControllerAdvice` in runtime-core) emit the envelope. The handler also covers Spring framework exceptions: `MethodArgumentNotValidException`, `ConstraintViolationException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `MissingRequestHeaderException`, `MethodArgumentTypeMismatchException`, `NoHandlerFoundException`, `HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`.
- Filters (where you can't throw): write the JSON envelope directly with all four fields. Example: `kelta-worker/src/main/java/io/kelta/worker/filter/TenantConcurrencyFilter.java`.
- Gateway-side: throw a `GatewayAuthenticationException` / `GatewayAuthorizationException` / `RouteNotFoundException` / `RateLimitExceededException` and let `io.kelta.gateway.error.GlobalErrorHandler` emit the envelope.
- For ad-hoc construction prefer `io.kelta.jsonapi.JsonApiResponseBuilder.error(status, code, title, detail)`.

When adding a new 4xx producer, add a test that asserts `errors[0]` carries non-empty `status`, `code`, `title`, and `detail` (see `kelta-platform/runtime/runtime-core/src/test/java/io/kelta/runtime/router/GlobalExceptionHandlerTest.java`).

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
