# UI API Client Migration Summary

## Issue
Multiple pages in emf-ui are using direct `fetch()` calls without authentication tokens, causing 401 errors.

## Solution
All pages need to use the `apiClient` from `ApiContext` which automatically includes Bearer tokens.

## Pages That Need Updates

### ✅ Already Fixed
- CollectionsPage - Uses apiClient
- CollectionDetailPage - Uses apiClient  
- CollectionFormPage - Uses apiClient
- RolesPage - Uses apiClient
- PoliciesPage - Uses apiClient
- OIDCProvidersPage - Uses apiClient

### ❌ Still Need Fixing

1. **PageBuilderPage** (`/ui/pages`)
   - Remove fetch functions (lines 120-220)
   - Add `const { apiClient } = useApi()`
   - Update component to use apiClient

2. **MenuBuilderPage** (`/ui/menus`)
   - Already uses correct routes
   - Remove fetch functions (lines 87-157)
   - Add `const { apiClient } = useApi()`
   - Update component to use apiClient

3. **PackagesPage** (`/control/packages`)
   - Already uses correct routes
   - Remove fetch functions (lines 108-220)
   - Add `const { apiClient } = useApi()`
   - Update component to use apiClient

4. **MigrationsPage** (`/control/migrations`)
   - Already uses correct routes
   - Remove fetch functions (lines 114-220)
   - Add `const { apiClient } = useApi()`
   - Update component to use apiClient

5. **ResourceBrowserPage** (`/resources`)
   - Remove fetchCollections function
   - Add `const { apiClient } = useApi()`
   - Update query to use apiClient

6. **ResourceListPage** (`/resources/:collection`)
   - Route needs fixing: `/api/_admin/collections/` → `/control/collections/`
   - Remove fetch functions
   - Add `const { apiClient } = useApi()`
   - Update queries to use apiClient

7. **ResourceDetailPage** (`/resources/:collection/:id`)
   - Already uses correct route for collections
   - Remove fetch functions
   - Add `const { apiClient } = useApi()`
   - Update queries to use apiClient

8. **ResourceFormPage** (`/resources/:collection/new` or `/resources/:collection/:id/edit`)
   - Already uses correct route for collections
   - Remove fetch functions
   - Add `const { apiClient } = useApi()`
   - Update queries to use apiClient

9. **DashboardPage** (`/`)
   - Route needs fixing: `/api/_admin/dashboard` → `/control/dashboard`
   - Remove fetchDashboardData function
   - Add `const { apiClient } = useApi()`
   - Update query to use apiClient

## Pattern for Fixing

### 1. Add import
```typescript
import { useApi } from '../../context/ApiContext';
```

### 2. Remove async fetch functions
Delete all `async function fetch*()` and `async function create/update/delete*()` functions

### 3. Add useApi hook in component
```typescript
const { apiClient } = useApi();
```

### 4. Update queries
```typescript
// Before
queryFn: fetchSomething

// After  
queryFn: () => apiClient.get<Type>('/route')
```

### 5. Update mutations
```typescript
// Before
mutationFn: createSomething

// After
mutationFn: (data) => apiClient.post<Type>('/route', data)
```

## Priority Order
1. PageBuilderPage - User is currently trying to access
2. MenuBuilderPage - Related to Pages
3. DashboardPage - Home page
4. Resource pages - Core functionality
5. PackagesPage, MigrationsPage - Admin features
