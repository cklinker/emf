# Coding Conventions

**Analysis Date:** 2026-03-22

## Java Conventions

### Naming Patterns

**Classes:** PascalCase (`RouteDefinition`, `CollectionLifecycleManager`, `ConfigEventListener`)
**Methods:** camelCase (`getId()`, `hasRateLimit()`, `refreshCollectionDefinition()`)
**Constants:** UPPER_SNAKE_CASE (`TAG_TENANT`, `TAG_METHOD`, `UNKNOWN`)
**Fields:** camelCase with `private final` (`private final MeterRegistry registry`)
**Booleans:** `is`/`has` prefix for getters (`isActive()`, `hasRateLimit()`)
**Packages:** `io.kelta.<service>.<feature>` (e.g., `io.kelta.authz.cerbos`)

### Code Style

**Formatting:**
- 4-space indentation
- Open braces on same line (1TBS)
- No wildcard imports
- Imports sorted alphabetically within groups

**Import Order:**
1. Project-internal (`io.kelta.*`)
2. External libraries (`com.fasterxml.*`, `org.*`)
3. Java standard library (`java.*`, `javax.*`)

**Annotations:**
- `@Component` for Spring beans, `@Service` for business logic, `@Repository` for data access
- Constructor injection preferred over field injection
- `@KafkaListener` with explicit topics and group IDs
- `@Override` for interface implementations

### Logging

- SLF4J with Logback: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class)`
- Parameterized logging (not string concat): `logger.info("Processing: id={}, name={}", id, name)`
- Levels: debug, info, warn, error
- JSON structured output via Logstash Logback Encoder

### Error Handling

- Try/catch with full exception logging: `logger.error("Error: {}", e.getMessage(), e)`
- Graceful degradation on non-critical failures
- Custom exceptions extend from base types
- No silent failures

### Javadoc

- Required for public classes and methods
- HTML tags: `<p>`, `<h3>`, `<ol>`, `<li>`, `{@code}`, `{@link}`
- Method docs include `@param`, `@returns`, `@throws`

## TypeScript Conventions

### Naming Patterns

**Files:** PascalCase for components (`FilterBuilder.tsx`), camelCase for modules in SDK
**Classes:** PascalCase (`TokenManager`, `KeltaClient`, `ValidationError`)
**Interfaces:** PascalCase, no `I` prefix (`TokenState`, `TokenProvider`)
**Functions:** camelCase (`parseTokenExpiration()`, `mapAxiosError()`)
**Constants:** UPPER_SNAKE_CASE or camelCase depending on scope
**Enums:** PascalCase name, UPPER_SNAKE_CASE values

### Code Style (kelta-web)

**Prettier** (`.prettierrc`):
- 2-space indentation
- Single quotes
- Semicolons required
- Trailing commas (ES5)
- 100 char line width
- Arrow parens always
- Unix line endings (LF)

**Note:** `kelta-ui/app/.prettierrc` omits semicolons (`"semi": false`)

### ESLint Rules (kelta-web)

- `@typescript-eslint/no-explicit-any`: warn
- `@typescript-eslint/no-floating-promises`: error
- `@typescript-eslint/no-misused-promises`: error
- `@typescript-eslint/no-unused-vars`: error (ignore `_` prefix)
- `react-hooks/rules-of-hooks`: error
- `react-hooks/exhaustive-deps`: warn
- `no-console`: warn (allow warn/error)
- `prefer-const`: error
- `no-var`: error

### Import Organization

**Order:**
1. Type imports (`import type { ... }`)
2. Internal modules (`@kelta/sdk`, `@kelta/components`)
3. Relative imports (`./`, `../`)
4. External packages (`react`, `zod`)

**Path Aliases:** `@kelta/sdk`, `@kelta/components`, `@kelta/plugin-sdk`

### Error Handling

- Custom error hierarchy: `KeltaError extends Error` with `statusCode`, `details`
- `Object.setPrototypeOf()` for proper prototype chain
- `mapAxiosError()` for HTTP error translation
- Type guards for error detection

### Type Safety

- Generics for type-safe resource ops: `ResourceClient<T>`
- Explicit return types on public methods
- Readonly properties for immutability
- Zod schemas for runtime validation

### Module Design

- Named exports preferred
- Barrel files (`index.ts`) for public API
- Default exports only for React components

---

*Convention analysis: 2026-03-22*
*Update when patterns change*
