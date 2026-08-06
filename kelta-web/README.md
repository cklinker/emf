# Kelta Web

TypeScript monorepo containing the SDK, React component library, plugin SDK, CLI, and formula engine for building Kelta applications.

## Packages

```
kelta-web/packages/
├── sdk/            # @kelta/sdk         — Type-safe Kelta API client
├── components/     # @kelta/components  — Reusable React components
├── plugin-sdk/     # @kelta/plugin-sdk  — Plugin development toolkit
├── cli/            # @kelta/cli         — Command-line tool built on Commander.js
└── formula/        # @kelta/formula     — Formula/expression evaluation for validation + computed fields
```

### @kelta/sdk

Type-safe TypeScript client for Kelta APIs with validation, error handling, and retry logic.

**Core classes:**
- `KeltaClient` -- Main client with auto-discovery, token management, and configurable retry
- `ResourceClient<T>` -- CRUD operations (`list`, `get`, `create`, `update`, `patch`, `delete`)
- `QueryBuilder<T>` -- Fluent API for building queries with pagination, sorting, filtering, and field selection
- `AdminClient` -- Platform administration operations (collections, fields, roles, policies, webhooks)
- `TokenManager` -- Token lifecycle management (refresh, validation)

**Error classes:**
- `KeltaError`, `ValidationError`, `AuthenticationError`, `AuthorizationError`, `NotFoundError`, `ServerError`, `NetworkError`

**Validation:**
- Zod schemas for `ResourceMetadata`, `ListResponse`, `ErrorResponse`, `FieldDefinition`

**Type generation:**
- `generateTypesFromUrl()`, `generateTypesFromSpec()` -- Generate TypeScript types from OpenAPI specs

**Field types supported:** `string`, `number`, `boolean`, `date`, `datetime`, `json`, `reference`, `picklist`, `multi_picklist`, `currency`, `percent`, `auto_number`, `phone`, `email`, `url`, `rich_text`, `encrypted`, `external_id`, `geolocation`, `lookup`, `master_detail`, `formula`, `rollup_summary`

### @kelta/components

Reusable React component library for building Kelta UIs.

**Providers & hooks:**
- `KeltaProvider` -- Context provider wrapping client and user
- `useKeltaClient()`, `useCurrentUser()` -- Client and user access
- `useResource<T>()` -- Fetch and mutate a single resource
- `useResourceList<T>()` -- Fetch paginated resource lists
- `useDiscovery()` -- Discover available resources

**Components:**
- `DataTable<T>` -- Data grid with sorting, filtering, pagination, row selection
- `ResourceForm` -- Auto-generated forms with validation and custom field renderers
- `ResourceDetail` -- Record detail display with field renderer registry
- `FilterBuilder` -- Dynamic filter UI
- `Navigation` -- Menu/navigation component
- `PageLayout`, `TwoColumnLayout`, `ThreeColumnLayout` -- Responsive layouts
- `LayoutRenderer` -- Dynamic layout rendering from config

### @kelta/cli

The `kelta` command-line tool (see `.claude/docs/specs/kelta-cli/` for the roadmap). Built on
a **command registry** (`src/registry/`): every command is a data record with a zod input
schema and a handler returning structured data — the parser, help, and (in later slices) the
machine-readable manifest and local MCP tools all derive from it. HTTP goes through
`@kelta/sdk` (`KeltaClient`/`ResourceClient`/`AdminClient`), not a private client.

**Profiles & auth** — named profiles in `~/.kelta/config.json` plus credentials (PATs) in
`~/.kelta/credentials.json` (mode 0600). Legacy `~/.keltarc` migrates silently on first run.
Precedence: flags > env (`KELTA_PROFILE`, `KELTA_URL`, `KELTA_TENANT`, `KELTA_TOKEN`) >
profile file. `KELTA_CONFIG_DIR` relocates the config dir (CI/tests).

```bash
kelta auth login --url https://api.kelta.io --tenant acme --token klt_… --profile prod
kelta profile list|use|show|remove|rename
kelta collections list --output json
kelta records list invoices --filter status=open --filter amount.gte=100 --sort -createdAt
kelta records create invoices --data @invoice.json     # or inline JSON, or - for stdin
kelta metadata export -n app -v 1.0 -o app.json        # file flag is -o/--out
kelta sandbox create -n dev && kelta promote create -s <src> -t <dst>
```

**Output contract** (machine-first): global `--output table|json|yaml|csv|ndjson` — defaults
to `table` on a TTY and `json` when piped; `--raw` emits the unflattened JSON:API envelope;
`--quiet` prints ids only. Flattened rows are `{ id, ...attributes, <toOneRel>: id }`. Errors
are single-line JSON on stderr (`{"error":{code,status,detail,requestId}}`) with stable exit
codes: 0 ok, 1 API, 2 usage, 3 auth, 4 not-found, 5 conflict/rate-limit. Destructive
commands confirm on a TTY and require `--yes` otherwise.

### @kelta/plugin-sdk

Plugin development toolkit for extending Kelta UIs.

- `BasePlugin` -- Abstract base class with `init()`, `mount()`, `unmount()` lifecycle
- `ComponentRegistry` -- Static registry for custom field renderers and page components
- `PluginContext` -- Provides `KeltaClient`, current user, and router to plugins

## Package Dependencies

```
@kelta/components ──► @kelta/sdk (peer)
@kelta/plugin-sdk ──► @kelta/sdk (peer)
@kelta/cli        ──► @kelta/sdk (runtime — build sdk before cli)
```

## Scripts

```bash
npm run build            # Build all packages
npm run test:coverage    # Run tests with coverage (80% threshold)
npm run lint             # ESLint check
npm run typecheck        # TypeScript validation
npm run format:check     # Prettier check
npm run generate-types   # Generate types from OpenAPI spec
```

## Development

```bash
cd kelta-web
npm install
npm run build
```

## Testing

Vitest with jsdom, Testing Library, MSW for API mocking, and fast-check for property-based tests.

```bash
npm run test             # Watch mode
npm run test:coverage    # Coverage report (80% branch/function/line/statement threshold)
```

## Tech Stack

- TypeScript 5.3.3, React 19.2.0, Vite 5.1.4, Vitest 1.3.1
- Axios 1.6.7, Zod 3.22.4
- TanStack React Query 5.24.1, React Hook Form 7.51.0
- React Router DOM 6.22.2
- ESLint 8.57.0, Prettier 3.2.5
- Node.js >= 18.0.0
