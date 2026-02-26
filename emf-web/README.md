# EMF Web

TypeScript monorepo containing the SDK, React component library, and plugin SDK for building EMF applications.

## Packages

```
emf-web/packages/
├── sdk/            # @emf/sdk        — Type-safe EMF API client
├── components/     # @emf/components  — Reusable React components
└── plugin-sdk/     # @emf/plugin-sdk  — Plugin development toolkit
```

### @emf/sdk

Type-safe TypeScript client for EMF APIs with validation, error handling, and retry logic.

**Core classes:**
- `EMFClient` -- Main client with auto-discovery, token management, and configurable retry
- `ResourceClient<T>` -- CRUD operations (`list`, `get`, `create`, `update`, `patch`, `delete`)
- `QueryBuilder<T>` -- Fluent API for building queries with pagination, sorting, filtering, and field selection
- `AdminClient` -- Platform administration operations (collections, fields, roles, policies, webhooks)
- `TokenManager` -- Token lifecycle management (refresh, validation)

**Error classes:**
- `EMFError`, `ValidationError`, `AuthenticationError`, `AuthorizationError`, `NotFoundError`, `ServerError`, `NetworkError`

**Validation:**
- Zod schemas for `ResourceMetadata`, `ListResponse`, `ErrorResponse`, `FieldDefinition`

**Type generation:**
- `generateTypesFromUrl()`, `generateTypesFromSpec()` -- Generate TypeScript types from OpenAPI specs

**Field types supported:** `string`, `number`, `boolean`, `date`, `datetime`, `json`, `reference`, `picklist`, `multi_picklist`, `currency`, `percent`, `auto_number`, `phone`, `email`, `url`, `rich_text`, `encrypted`, `external_id`, `geolocation`, `lookup`, `master_detail`, `formula`, `rollup_summary`

### @emf/components

Reusable React component library for building EMF UIs.

**Providers & hooks:**
- `EMFProvider` -- Context provider wrapping client and user
- `useEMFClient()`, `useCurrentUser()` -- Client and user access
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

### @emf/plugin-sdk

Plugin development toolkit for extending EMF UIs.

- `BasePlugin` -- Abstract base class with `init()`, `mount()`, `unmount()` lifecycle
- `ComponentRegistry` -- Static registry for custom field renderers and page components
- `PluginContext` -- Provides `EMFClient`, current user, and router to plugins

## Package Dependencies

```
@emf/components ──► @emf/sdk (peer)
@emf/plugin-sdk ──► @emf/sdk (peer)
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
cd emf-web
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

- TypeScript 5.3.3, React 18.2.0, Vite 5.1.4, Vitest 1.3.1
- Axios 1.6.7, Zod 3.22.4
- TanStack React Query 5.24.1, React Hook Form 7.51.0
- React Router DOM 6.22.2
- ESLint 8.57.0, Prettier 3.2.5
- Node.js >= 18.0.0
