# EMF UI

Admin and end-user UI for the EMF platform. Built with React 19, TypeScript, Vite, and Tailwind CSS. Provides a full-featured admin/builder interface for managing collections, fields, users, permissions, workflows, and more -- plus an end-user runtime for browsing data.

## Features

- Multi-tenant routing (`/:tenantSlug/...`)
- OIDC authentication with provider selection
- Admin setup area for platform configuration
- End-user runtime with dynamic collection browsing
- RBAC with route-level and field-level permission guards
- Dark/light theme support
- WCAG 2.1 Level AA accessibility
- Global search (`Cmd+K` / `Ctrl+K`)
- Keyboard shortcuts

## Routes

### Admin Routes (`/:tenantSlug/...`)

| Area | Routes |
|------|--------|
| Collections | `/collections`, `/collections/new` (wizard), `/collections/:id`, `/collections/:id/edit` |
| Data | `/resources`, `/resources/:collection`, `/resources/:collection/new`, `/resources/:collection/:id` |
| Schema | `/picklists`, `/layouts`, `/listviews` |
| Automation | `/workflow-rules`, `/workflow-action-types`, `/approvals`, `/flows`, `/scheduled-jobs`, `/email-templates` |
| Security | `/users`, `/users/:id`, `/profiles`, `/profiles/:id`, `/permission-sets`, `/permission-sets/:id` |
| Audit | `/audit-trail`, `/login-history`, `/security-audit` |
| Integration | `/connected-apps`, `/scripts`, `/webhooks`, `/oidc-providers` |
| Platform | `/pages`, `/menus`, `/packages`, `/migrations`, `/plugins` |
| System | `/setup`, `/system-health`, `/governor-limits`, `/tenants`, `/tenant-dashboard` |

### End-User Routes (`/:tenantSlug/app/...`)

| Route | Description |
|-------|-------------|
| `/home` | User dashboard |
| `/o/:collection` | Record list |
| `/o/:collection/new` | Create record |
| `/o/:collection/:id` | Record detail |
| `/o/:collection/:id/edit` | Edit record |
| `/search` | Global search |
| `/p/:pageSlug` | Custom pages |

## Context Providers

The app wraps routes in a provider hierarchy:

`BrowserRouter` > `QueryClient` > `ErrorBoundary` > `TenantProvider` > `AuthProvider` > `ApiProvider` > `ConfigProvider` > `ThemeProvider` > `I18nProvider` > `PluginProvider` > `AppContext` > `Toasts` > `LiveRegion`

## Key Hooks

| Hook | Purpose |
|------|---------|
| `useCollectionRecords` | Fetch paginated records with filtering/sorting |
| `useCollectionSchema` | Fetch field definitions |
| `useCollectionSummaries` | Cached list of all collections |
| `useCollectionPermissions` | Check CRUD permissions on a collection |
| `useFieldPermissions` | Field-level read/write permissions |
| `useObjectPermissions` | Record-level permissions |
| `usePageLayout` | Page layout configuration |
| `useCreateResource` | Create mutation |
| `useDeleteResource` | Delete mutation |
| `useGlobalShortcuts` | Global keyboard shortcut state |
| `useLookupDisplayMap` | Map lookup IDs to display values |

## Development

```bash
cd emf-ui/app
npm install
npm run dev    # Starts on http://localhost:5174
```

The dev server proxies API requests (`/control/*`, `/internal/*`, `/actuator/*`) to `http://localhost:8081`.

## Scripts

```bash
npm run dev              # Vite dev server (port 5174)
npm run build            # TypeScript check + production build
npm run lint             # ESLint (includes JSX a11y rules)
npm run format:check     # Prettier check
npm run test:run         # Vitest (CI mode)
npm run test:coverage    # Vitest with coverage
```

## Testing

Vitest with jsdom, Testing Library, MSW for API mocking, and fast-check for property-based tests.

```bash
npm run test:run         # Single run
npm run test:coverage    # With coverage report
```

## Tech Stack

- React 19.2.0, TypeScript 5.9.3, Vite 7.2.4
- Tailwind CSS 4.1.18, Radix UI, shadcn/ui components
- TanStack React Query 5.90.20, React Hook Form 7.71.1
- React Router DOM 7.13.0, Zod 4.3.6, Axios 1.13.5
- Vitest 4.0.18, Testing Library, MSW 2.12.7
- ESLint 9.39.1 (with jsx-a11y), Prettier 3.8.1
- @emf/sdk, @emf/components, @emf/plugin-sdk
