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

## MCP tools (kelta-mcp)

### Friendly args → native JSON:API body
MCP admin tools expose camelCase, lowercase-friendly argument names to LLM callers and translate them to the native worker payload before hitting the gateway. The worker is strict — uppercase enums, `name` (not `fieldName`), `collectionId` (not `collectionName`), `uniqueConstraint` (not `unique`) — so do the translation at the tool boundary, not in the prompt.

- **Field types**: accept friendly aliases (`text`, `number`, `picklist`, `lookup`, …) and map to the uppercase `FieldType` enum the worker expects. Centralise the mapping in `FieldBodyBuilder.resolveNativeType` so `add_field` and `create_collection`'s nested field array stay in sync.
- **Per-type payload**: picklist fields need `attributes.fieldTypeConfig = {picklistSourceType, picklistSourceId}`; reference/lookup fields need `relationships.referenceCollectionId.data.id` + `attributes.relationshipName` (and optional `relationshipType`). Build these in the same helper so the on-the-wire shape can be unit-tested with WireMock JSON path matchers.
- **ID resolution**: accept either a UUID (`collectionId`, `referenceCollectionId`, `picklistSourceId`) or a friendly name (`collectionName`, `referenceCollection`) and resolve names via `GET /api/collections?filter[name][eq]=…`. UUIDs match `FieldBodyBuilder.UUID_PATTERN`; anything else triggers the lookup.
