# Troubleshooting Guide

Common issues and solutions when using EMF web packages.

## Installation Issues

### Peer Dependency Warnings

**Problem:** npm shows peer dependency warnings during installation.

**Solution:** Ensure you have the required peer dependencies installed:

```bash
# For @emf/components
npm install react@^18 react-dom@^18 react-router-dom@^6 @tanstack/react-query@^5

# For @emf/plugin-sdk
npm install react@^18 react-router-dom@^6 @emf/sdk
```

### TypeScript Version Mismatch

**Problem:** Type errors due to TypeScript version incompatibility.

**Solution:** Use TypeScript 5.0 or higher:

```bash
npm install typescript@^5
```

---

## SDK Issues

### "Network Error" on All Requests

**Problem:** All API requests fail with NetworkError.

**Possible Causes:**
1. Incorrect base URL
2. CORS not configured on the server
3. Network connectivity issues

**Solutions:**

1. Verify the base URL:
```typescript
const client = new EMFClient({
  baseUrl: 'https://api.example.com', // No trailing slash
});
```

2. Check CORS configuration on your EMF server.

3. Test the API directly:
```bash
curl https://api.example.com/api/_meta/resources
```

### "AuthenticationError" Despite Valid Token

**Problem:** Requests fail with 401 even though the token is valid.

**Possible Causes:**
1. Token not being included in requests
2. Token expired
3. Token format incorrect

**Solutions:**

1. Verify token provider is returning the token:
```typescript
const client = new EMFClient({
  baseUrl: 'https://api.example.com',
  tokenProvider: {
    getToken: async () => {
      const token = localStorage.getItem('auth_token');
      console.log('Token:', token); // Debug
      return token;
    },
  },
});
```

2. Check token expiration and implement refresh:
```typescript
tokenProvider: {
  getToken: async () => localStorage.getItem('auth_token'),
  refreshToken: async () => {
    // Implement token refresh
  },
},
```

### "ValidationError" on Create/Update

**Problem:** Create or update operations fail with validation errors.

**Solution:** Check the fieldErrors property for details:

```typescript
try {
  await users.create(data);
} catch (error) {
  if (error instanceof ValidationError) {
    console.log('Field errors:', error.fieldErrors);
    // { "email": ["Email is required", "Invalid email format"] }
  }
}
```

### Discovery Returns Empty Array

**Problem:** `client.discover()` returns an empty array.

**Possible Causes:**
1. No collections configured in the control plane
2. User lacks permission to view collections
3. Discovery endpoint not available

**Solutions:**

1. Verify collections exist in the control plane.
2. Check user permissions.
3. Test the discovery endpoint directly:
```bash
curl https://api.example.com/api/_meta/resources
```

### Query Builder Not Applying Filters

**Problem:** Filters added to QueryBuilder don't affect results.

**Solution:** Ensure you're calling `execute()`:

```typescript
// Wrong - filters not applied
const results = users.query()
  .filter('status', 'eq', 'active');

// Correct
const results = await users.query()
  .filter('status', 'eq', 'active')
  .execute(); // Don't forget this!
```

---

## Component Issues

### DataTable Shows "Loading" Forever

**Problem:** DataTable stays in loading state indefinitely.

**Possible Causes:**
1. QueryClient not provided
2. Resource name incorrect
3. API request failing silently

**Solutions:**

1. Ensure QueryClientProvider is set up:
```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <YourApp />
    </QueryClientProvider>
  );
}
```

2. Verify the resource name matches your API.

3. Check browser console for errors.

### ResourceForm Not Generating Fields

**Problem:** ResourceForm renders empty or with missing fields.

**Possible Causes:**
1. Schema fetch failed
2. Collection has no fields defined
3. Field types not supported

**Solutions:**

1. Check if schema is being fetched:
```typescript
const resources = await client.discover();
const userResource = resources.find(r => r.name === 'users');
console.log('Fields:', userResource?.fields);
```

2. Verify fields are defined in the control plane.

### Navigation Not Highlighting Active Item

**Problem:** Current route doesn't highlight in Navigation.

**Solution:** Ensure you're using React Router and the paths match:

```tsx
import { BrowserRouter } from 'react-router-dom';

// Paths must match exactly
const menuItems = [
  { id: 'users', label: 'Users', path: '/users' }, // Not '/users/'
];

function App() {
  return (
    <BrowserRouter>
      <Navigation items={menuItems} />
    </BrowserRouter>
  );
}
```

### FilterBuilder Operators Not Showing

**Problem:** FilterBuilder shows no operators for a field.

**Solution:** Ensure field types are correctly defined:

```typescript
const fields: FieldDefinition[] = [
  { name: 'name', type: 'string' },  // Shows: eq, contains, startsWith, endsWith
  { name: 'age', type: 'number' },   // Shows: eq, gt, gte, lt, lte
  { name: 'active', type: 'boolean' }, // Shows: eq
  { name: 'created', type: 'date' },   // Shows: eq, gt, gte, lt, lte
];
```

---

## Plugin Issues

### Plugin Context is Undefined

**Problem:** `this.context` is undefined in plugin methods.

**Solution:** Always call `super.init(context)` in your init method:

```typescript
class MyPlugin extends BasePlugin {
  async init(context: PluginContext): Promise<void> {
    super.init(context); // Don't forget this!
    // Your initialization code
  }
}
```

### Custom Field Renderer Not Used

**Problem:** Registered field renderer is not being used.

**Possible Causes:**
1. Field type doesn't match
2. Registration happens after component renders
3. Another renderer overwrites it

**Solutions:**

1. Verify field type matches exactly:
```typescript
// If field type is 'rating', register for 'rating'
ComponentRegistry.registerFieldRenderer('rating', RatingRenderer);
```

2. Register renderers before rendering components:
```typescript
// In your app initialization
ComponentRegistry.registerFieldRenderer('rating', RatingRenderer);

// Then render components
ReactDOM.render(<App />, document.getElementById('root'));
```

### Page Component Not Rendering

**Problem:** Registered page component doesn't appear at its route.

**Solution:** Ensure the route is integrated with your router:

```tsx
import { ComponentRegistry } from '@emf/plugin-sdk';
import { Routes, Route } from 'react-router-dom';

// Get all registered page components
const pageComponents = ComponentRegistry.getAllPageComponents();

function App() {
  return (
    <Routes>
      {pageComponents.map(({ name, route, component: Component }) => (
        <Route
          key={name}
          path={route}
          element={<Component client={client} params={{}} user={user} />}
        />
      ))}
    </Routes>
  );
}
```

---

## Build Issues

### "Cannot find module '@emf/sdk'"

**Problem:** TypeScript can't find the SDK module.

**Solutions:**

1. Ensure the package is installed:
```bash
npm install @emf/sdk
```

2. Check your tsconfig.json:
```json
{
  "compilerOptions": {
    "moduleResolution": "node"
  }
}
```

### Type Declarations Not Found

**Problem:** TypeScript shows "Could not find declaration file" errors.

**Solution:** The packages include type declarations. If issues persist:

1. Delete node_modules and reinstall:
```bash
rm -rf node_modules package-lock.json
npm install
```

2. Restart your TypeScript server in your IDE.

### Bundle Size Too Large

**Problem:** Production bundle is larger than expected.

**Solutions:**

1. Use tree-shaking by importing only what you need:
```typescript
// Good - tree-shakeable
import { EMFClient } from '@emf/sdk';
import { DataTable } from '@emf/components';

// Avoid - imports everything
import * as SDK from '@emf/sdk';
```

2. Check your bundler configuration for proper tree-shaking support.

---

## Performance Issues

### Slow Initial Load

**Problem:** Application takes too long to load initially.

**Solutions:**

1. Use code splitting:
```typescript
const DataTable = React.lazy(() => import('@emf/components').then(m => ({ default: m.DataTable })));
```

2. Reduce discovery cache TTL if data changes frequently:
```typescript
const client = new EMFClient({
  baseUrl: 'https://api.example.com',
  cache: {
    discoveryTTL: 60000, // 1 minute instead of default 5 minutes
  },
});
```

### Too Many API Requests

**Problem:** Application makes excessive API calls.

**Solutions:**

1. Increase cache TTL:
```typescript
const client = new EMFClient({
  cache: {
    discoveryTTL: 600000, // 10 minutes
  },
});
```

2. Use React Query's caching effectively:
```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      cacheTime: 10 * 60 * 1000, // 10 minutes
    },
  },
});
```

---

## FAQ

### Can I use the SDK without React?

Yes! The `@emf/sdk` package has no React dependencies and can be used in any JavaScript/TypeScript environment.

### How do I handle token refresh?

Implement the `refreshToken` method in your TokenProvider:

```typescript
tokenProvider: {
  getToken: async () => localStorage.getItem('token'),
  refreshToken: async () => {
    const response = await fetch('/auth/refresh', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${localStorage.getItem('refreshToken')}` },
    });
    const { token } = await response.json();
    localStorage.setItem('token', token);
    return token;
  },
},
```

### How do I disable response validation?

Set `validation: false` in the client config:

```typescript
const client = new EMFClient({
  baseUrl: 'https://api.example.com',
  validation: false,
});
```

### Can I use custom HTTP headers?

Currently, custom headers should be added via the token provider or by extending the client.

### How do I test components that use EMF?

Use MSW (Mock Service Worker) to mock API responses:

```typescript
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

const server = setupServer(
  http.get('*/api/users', () => {
    return HttpResponse.json({
      data: [{ id: '1', name: 'Test User' }],
      pagination: { page: 1, size: 10, total: 1, totalPages: 1 },
    });
  }),
);

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

---

## Getting Help

If you can't find a solution here:

1. Check the [GitHub Issues](https://github.com/emf/emf-web/issues)
2. Search existing issues for similar problems
3. Create a new issue with:
   - Package version
   - Node.js version
   - Minimal reproduction code
   - Expected vs actual behavior
