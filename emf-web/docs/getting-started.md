# Getting Started with EMF Web Packages

This guide will help you get started with the EMF (Enterprise Microservice Framework) web packages for building type-safe, modern web applications.

## Overview

EMF provides three npm packages:

| Package | Description |
|---------|-------------|
| `@emf/sdk` | Type-safe TypeScript client for EMF APIs |
| `@emf/components` | Reusable React component library |
| `@emf/plugin-sdk` | Plugin development toolkit |

## Prerequisites

- Node.js 18+ 
- npm 9+ or yarn 1.22+
- React 18+ (for components and plugin-sdk)
- TypeScript 5+ (recommended)

## Installation

### SDK Only

For backend integrations or non-React applications:

```bash
npm install @emf/sdk
```

### SDK + Components

For React applications:

```bash
npm install @emf/sdk @emf/components react react-dom react-router-dom @tanstack/react-query
```

### Plugin Development

For building EMF plugins:

```bash
npm install @emf/plugin-sdk @emf/sdk react react-dom
```

## Quick Start

### 1. Initialize the EMF Client

```typescript
import { EMFClient } from '@emf/sdk';

const client = new EMFClient({
  baseUrl: 'https://your-emf-api.example.com',
});
```

### 2. Discover Available Resources

```typescript
const resources = await client.discover();
console.log('Available resources:', resources.map(r => r.name));
```

### 3. Perform CRUD Operations

```typescript
// Get a resource client
const users = client.resource('users');

// List users
const userList = await users.list({ page: 1, size: 10 });

// Get a single user
const user = await users.get('user-123');

// Create a user
const newUser = await users.create({
  name: 'John Doe',
  email: 'john@example.com',
});

// Update a user
await users.update('user-123', { ...user, name: 'John Smith' });

// Delete a user
await users.delete('user-123');
```

### 4. Use the Query Builder

```typescript
const results = await users
  .query()
  .paginate(1, 20)
  .sort('createdAt', 'desc')
  .filter('status', 'eq', 'active')
  .execute();
```

## React Application Setup

### 1. Set Up Providers

```tsx
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';

const queryClient = new QueryClient();

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <YourApp />
      </BrowserRouter>
    </QueryClientProvider>
  );
}
```

### 2. Use Components

```tsx
import { DataTable, ResourceForm, Navigation } from '@emf/components';

function UserList() {
  return (
    <DataTable
      resourceName="users"
      columns={[
        { field: 'name', header: 'Name', sortable: true },
        { field: 'email', header: 'Email' },
      ]}
    />
  );
}
```

## Authentication

### Token Provider

```typescript
const client = new EMFClient({
  baseUrl: 'https://api.example.com',
  tokenProvider: {
    getToken: async () => {
      return localStorage.getItem('auth_token');
    },
    refreshToken: async () => {
      const response = await fetch('/auth/refresh');
      const { token } = await response.json();
      localStorage.setItem('auth_token', token);
      return token;
    },
  },
});
```

## Error Handling

```typescript
import {
  ValidationError,
  AuthenticationError,
  NotFoundError,
} from '@emf/sdk';

try {
  await users.create({ name: '' });
} catch (error) {
  if (error instanceof ValidationError) {
    console.error('Validation errors:', error.fieldErrors);
  } else if (error instanceof AuthenticationError) {
    // Redirect to login
  } else if (error instanceof NotFoundError) {
    // Show 404 page
  }
}
```

## Type Generation

Generate TypeScript types from your EMF API:

```bash
npx @emf/sdk generate-types \
  --url https://api.example.com/openapi.json \
  --output ./src/types/emf.ts
```

Then use the generated types:

```typescript
import type { User, Product } from './types/emf';

const users = client.resource<User>('users');
const user = await users.get('123'); // user is typed as User
```

## Configuration Options

### EMFClient Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `baseUrl` | `string` | required | Base URL of the EMF API |
| `tokenProvider` | `TokenProvider` | `undefined` | Token provider for authentication |
| `cache.discoveryTTL` | `number` | `300000` | Discovery cache TTL in ms |
| `retry.maxAttempts` | `number` | `3` | Max retry attempts |
| `retry.backoffMultiplier` | `number` | `2` | Backoff multiplier |
| `retry.maxDelay` | `number` | `30000` | Max delay between retries |
| `validation` | `boolean` | `true` | Enable response validation |

## Next Steps

- [API Reference](./api-reference.md) - Detailed API documentation
- [Component Guide](./components.md) - Component usage examples
- [Plugin Development](./plugin-development.md) - Building custom plugins
- [Troubleshooting](./troubleshooting.md) - Common issues and solutions

## Support

- GitHub Issues: [emf-web/issues](https://github.com/emf/emf-web/issues)
- Documentation: [emf-docs](https://docs.emf.example.com)
