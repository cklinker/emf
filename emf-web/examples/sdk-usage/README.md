# @emf/sdk Usage Examples

This directory contains examples demonstrating how to use the `@emf/sdk` package.

## Installation

```bash
npm install @emf/sdk
```

## Examples

### Basic Client Initialization

```typescript
import { EMFClient } from '@emf/sdk';

// Create a client with just a base URL (unauthenticated)
const client = new EMFClient({
  baseUrl: 'https://api.example.com',
});

// Create a client with authentication
const authenticatedClient = new EMFClient({
  baseUrl: 'https://api.example.com',
  tokenProvider: {
    getToken: async () => localStorage.getItem('auth_token'),
    refreshToken: async () => {
      // Implement token refresh logic
      const response = await fetch('/auth/refresh');
      const { token } = await response.json();
      localStorage.setItem('auth_token', token);
      return token;
    },
  },
});

// Create a client with custom options
const customClient = new EMFClient({
  baseUrl: 'https://api.example.com',
  cache: {
    discoveryTTL: 60000, // Cache discovery for 1 minute
  },
  retry: {
    maxAttempts: 3,
    backoffMultiplier: 2,
    maxDelay: 10000,
  },
  validation: true, // Enable response validation
});
```

### Resource Discovery

```typescript
import { EMFClient } from '@emf/sdk';

const client = new EMFClient({ baseUrl: 'https://api.example.com' });

// Discover available resources
const resources = await client.discover();

console.log('Available resources:');
resources.forEach((resource) => {
  console.log(`- ${resource.name} (${resource.displayName})`);
  console.log(`  Fields: ${resource.fields.map((f) => f.name).join(', ')}`);
  console.log(`  Operations: ${resource.operations.join(', ')}`);
});
```

### CRUD Operations

```typescript
import { EMFClient } from '@emf/sdk';

const client = new EMFClient({ baseUrl: 'https://api.example.com' });

// Get a resource client for 'users'
const users = client.resource('users');

// List users with pagination
const userList = await users.list({
  page: 1,
  size: 10,
});
console.log(`Found ${userList.pagination.total} users`);

// Get a single user
const user = await users.get('user-123');
console.log(`User: ${user.name}`);

// Create a new user
const newUser = await users.create({
  name: 'John Doe',
  email: 'john@example.com',
});
console.log(`Created user: ${newUser.id}`);

// Update a user (full replacement)
const updatedUser = await users.update('user-123', {
  id: 'user-123',
  name: 'John Smith',
  email: 'john.smith@example.com',
});

// Patch a user (partial update)
const patchedUser = await users.patch('user-123', {
  name: 'John Smith Jr.',
});

// Delete a user
await users.delete('user-123');
```

### Query Builder

```typescript
import { EMFClient } from '@emf/sdk';

const client = new EMFClient({ baseUrl: 'https://api.example.com' });
const users = client.resource('users');

// Build a complex query using the fluent API
const results = await users
  .query()
  .paginate(1, 20)
  .sort('createdAt', 'desc')
  .sort('name', 'asc')
  .filter('status', 'eq', 'active')
  .filter('age', 'gte', 18)
  .fields('id', 'name', 'email', 'status')
  .execute();

console.log(`Found ${results.pagination.total} active adult users`);
results.data.forEach((user) => {
  console.log(`- ${user.name} (${user.email})`);
});
```

### Filtering Examples

```typescript
import { EMFClient } from '@emf/sdk';

const client = new EMFClient({ baseUrl: 'https://api.example.com' });
const products = client.resource('products');

// String filters
const searchResults = await products.list({
  filters: [
    { field: 'name', operator: 'contains', value: 'widget' },
    { field: 'category', operator: 'eq', value: 'electronics' },
  ],
});

// Numeric filters
const priceRange = await products.list({
  filters: [
    { field: 'price', operator: 'gte', value: 10 },
    { field: 'price', operator: 'lte', value: 100 },
  ],
});

// In operator for multiple values
const specificProducts = await products.list({
  filters: [{ field: 'status', operator: 'in', value: ['active', 'pending'] }],
});
```

### Error Handling

```typescript
import {
  EMFClient,
  ValidationError,
  AuthenticationError,
  AuthorizationError,
  NotFoundError,
  ServerError,
  NetworkError,
} from '@emf/sdk';

const client = new EMFClient({ baseUrl: 'https://api.example.com' });
const users = client.resource('users');

try {
  await users.create({ name: '' }); // Invalid data
} catch (error) {
  if (error instanceof ValidationError) {
    console.error('Validation failed:');
    Object.entries(error.fieldErrors).forEach(([field, errors]) => {
      console.error(`  ${field}: ${errors.join(', ')}`);
    });
  } else if (error instanceof AuthenticationError) {
    console.error('Not authenticated - please log in');
    // Redirect to login page
  } else if (error instanceof AuthorizationError) {
    console.error('Access denied - insufficient permissions');
  } else if (error instanceof NotFoundError) {
    console.error('Resource not found');
  } else if (error instanceof ServerError) {
    console.error(`Server error (${error.statusCode}): ${error.message}`);
  } else if (error instanceof NetworkError) {
    console.error('Network error - please check your connection');
  } else {
    throw error; // Re-throw unexpected errors
  }
}
```

### Admin Operations

```typescript
import { EMFClient } from '@emf/sdk';

const client = new EMFClient({
  baseUrl: 'https://api.example.com',
  tokenProvider: {
    getToken: async () => localStorage.getItem('admin_token'),
  },
});

// Collection management
const collections = await client.admin.collections.list();
console.log('Collections:', collections.map((c) => c.name));

// Create a new collection
const newCollection = await client.admin.collections.create({
  name: 'products',
  displayName: 'Products',
  fields: [
    { name: 'name', type: 'string', required: true },
    { name: 'price', type: 'number', required: true },
    { name: 'description', type: 'string' },
  ],
});

// Add a field to an existing collection
await client.admin.fields.add('products', {
  name: 'category',
  type: 'string',
  required: false,
});

// Role management
const roles = await client.admin.authz.listRoles();
await client.admin.authz.createRole({
  id: 'product-manager',
  name: 'Product Manager',
  permissions: ['products:read', 'products:write'],
});

// UI configuration
const uiConfig = await client.admin.ui.getConfig();
await client.admin.ui.updateConfig({
  ...uiConfig,
  theme: {
    primaryColor: '#007bff',
  },
});
```

## Type Generation

Generate TypeScript types from your EMF API:

```bash
npx @emf/sdk generate-types --url https://api.example.com/openapi.json --output ./src/types/emf.ts
```

This will create type definitions for all your collections:

```typescript
// Generated types
export interface User {
  id: string;
  name: string;
  email: string;
  createdAt: string;
}

export interface Product {
  id: string;
  name: string;
  price: number;
  description?: string;
}
```

Use the generated types with the SDK:

```typescript
import { EMFClient } from '@emf/sdk';
import type { User, Product } from './types/emf';

const client = new EMFClient({ baseUrl: 'https://api.example.com' });

// Type-safe resource operations
const users = client.resource<User>('users');
const userList = await users.list(); // userList.data is User[]

const products = client.resource<Product>('products');
const product = await products.get('prod-123'); // product is Product
```
