# @emf/components Usage Examples

This directory contains examples demonstrating how to use the `@emf/components` package.

## Installation

```bash
npm install @emf/components @emf/sdk react react-dom react-router-dom @tanstack/react-query
```

## Setup

Wrap your application with the required providers:

```tsx
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { EMFClient } from '@emf/sdk';

const queryClient = new QueryClient();
const emfClient = new EMFClient({ baseUrl: 'https://api.example.com' });

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

## Examples

### DataTable Component

Display a paginated, sortable, filterable table of resources:

```tsx
import React from 'react';
import { DataTable } from '@emf/components';
import type { ColumnDefinition } from '@emf/components';

interface User {
  id: string;
  name: string;
  email: string;
  status: string;
  createdAt: string;
}

const columns: ColumnDefinition<User>[] = [
  { field: 'name', header: 'Name', sortable: true, filterable: true },
  { field: 'email', header: 'Email', sortable: true },
  {
    field: 'status',
    header: 'Status',
    sortable: true,
    render: (value) => (
      <span className={`badge badge-${value}`}>{value}</span>
    ),
  },
  {
    field: 'createdAt',
    header: 'Created',
    sortable: true,
    render: (value) => new Date(value).toLocaleDateString(),
  },
];

function UserList() {
  const handleRowClick = (user: User) => {
    console.log('Selected user:', user);
  };

  return (
    <DataTable<User>
      resourceName="users"
      columns={columns}
      defaultSort={[{ field: 'createdAt', direction: 'desc' }]}
      pageSize={20}
      onRowClick={handleRowClick}
      selectable
      onSelectionChange={(selected) => {
        console.log('Selected users:', selected);
      }}
    />
  );
}
```

### DataTable with Filters

```tsx
import React, { useState } from 'react';
import { DataTable, FilterBuilder } from '@emf/components';
import type { FilterExpression, FieldDefinition } from '@emf/sdk';

const filterFields: FieldDefinition[] = [
  { name: 'name', type: 'string', displayName: 'Name' },
  { name: 'status', type: 'string', displayName: 'Status' },
  { name: 'age', type: 'number', displayName: 'Age' },
  { name: 'createdAt', type: 'date', displayName: 'Created Date' },
];

function FilterableUserList() {
  const [filters, setFilters] = useState<FilterExpression[]>([]);

  return (
    <div>
      <FilterBuilder
        fields={filterFields}
        value={filters}
        onChange={setFilters}
        maxFilters={5}
      />
      <DataTable
        resourceName="users"
        columns={columns}
        filters={filters}
      />
    </div>
  );
}
```

### ResourceForm Component

Create and edit resources with auto-generated forms:

```tsx
import React from 'react';
import { ResourceForm } from '@emf/components';
import { useNavigate, useParams } from 'react-router-dom';

// Create a new user
function CreateUser() {
  const navigate = useNavigate();

  return (
    <ResourceForm
      resourceName="users"
      onSave={(user) => {
        console.log('User created:', user);
        navigate(`/users/${user.id}`);
      }}
      onCancel={() => navigate('/users')}
    />
  );
}

// Edit an existing user
function EditUser() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  return (
    <ResourceForm
      resourceName="users"
      recordId={id}
      onSave={(user) => {
        console.log('User updated:', user);
        navigate(`/users/${user.id}`);
      }}
      onCancel={() => navigate(`/users/${id}`)}
    />
  );
}

// Read-only view
function ViewUser() {
  const { id } = useParams<{ id: string }>();

  return (
    <ResourceForm
      resourceName="users"
      recordId={id}
      readOnly
      onSave={() => {}}
      onCancel={() => {}}
    />
  );
}
```

### ResourceForm with Initial Values

```tsx
import React from 'react';
import { ResourceForm } from '@emf/components';

function CreateUserWithDefaults() {
  return (
    <ResourceForm
      resourceName="users"
      initialValues={{
        status: 'active',
        role: 'user',
        preferences: {
          notifications: true,
        },
      }}
      onSave={(user) => console.log('Created:', user)}
      onCancel={() => console.log('Cancelled')}
    />
  );
}
```

### ResourceDetail Component

Display resource details in read-only mode:

```tsx
import React from 'react';
import { ResourceDetail } from '@emf/components';
import { useParams } from 'react-router-dom';

function UserDetail() {
  const { id } = useParams<{ id: string }>();

  return (
    <ResourceDetail
      resourceName="users"
      recordId={id!}
      layout="vertical"
    />
  );
}

// With custom field renderers
function UserDetailCustom() {
  const { id } = useParams<{ id: string }>();

  return (
    <ResourceDetail
      resourceName="users"
      recordId={id!}
      layout="grid"
      customRenderers={{
        avatar: (value) => (
          <img src={value} alt="Avatar" className="avatar" />
        ),
        status: (value) => (
          <span className={`status-badge status-${value}`}>{value}</span>
        ),
      }}
    />
  );
}
```

### FilterBuilder Component

Build filter expressions interactively:

```tsx
import React, { useState } from 'react';
import { FilterBuilder } from '@emf/components';
import type { FilterExpression, FieldDefinition } from '@emf/sdk';

const fields: FieldDefinition[] = [
  { name: 'name', type: 'string', displayName: 'Name' },
  { name: 'email', type: 'string', displayName: 'Email' },
  { name: 'age', type: 'number', displayName: 'Age' },
  { name: 'status', type: 'string', displayName: 'Status' },
  { name: 'createdAt', type: 'date', displayName: 'Created Date' },
  { name: 'isActive', type: 'boolean', displayName: 'Active' },
];

function SearchPanel() {
  const [filters, setFilters] = useState<FilterExpression[]>([]);

  const handleSearch = () => {
    console.log('Searching with filters:', filters);
    // Use filters with your data fetching logic
  };

  return (
    <div className="search-panel">
      <FilterBuilder
        fields={fields}
        value={filters}
        onChange={setFilters}
        maxFilters={10}
      />
      <button onClick={handleSearch}>Search</button>
      <button onClick={() => setFilters([])}>Clear</button>
    </div>
  );
}
```

### Navigation Component

Build navigation menus with role-based filtering:

```tsx
import React from 'react';
import { Navigation } from '@emf/components';
import type { MenuItem, User } from '@emf/components';

const menuItems: MenuItem[] = [
  { id: 'home', label: 'Home', path: '/', icon: <HomeIcon /> },
  { id: 'users', label: 'Users', path: '/users', icon: <UsersIcon /> },
  {
    id: 'admin',
    label: 'Administration',
    icon: <SettingsIcon />,
    roles: ['admin'], // Only visible to admins
    children: [
      { id: 'collections', label: 'Collections', path: '/admin/collections' },
      { id: 'roles', label: 'Roles', path: '/admin/roles' },
      { id: 'settings', label: 'Settings', path: '/admin/settings' },
    ],
  },
  {
    id: 'reports',
    label: 'Reports',
    path: '/reports',
    roles: ['admin', 'manager'],
  },
];

function AppNavigation() {
  const currentUser: User = {
    id: '1',
    username: 'john',
    roles: ['admin'],
  };

  return (
    <Navigation
      items={menuItems}
      currentUser={currentUser}
      orientation="vertical"
      collapsible
    />
  );
}

// Horizontal navigation (top menu)
function TopNavigation() {
  return (
    <Navigation
      items={menuItems}
      orientation="horizontal"
    />
  );
}
```

### Layout Components

Structure your pages consistently:

```tsx
import React from 'react';
import {
  PageLayout,
  TwoColumnLayout,
  ThreeColumnLayout,
} from '@emf/components';

// Basic page layout
function BasicPage() {
  return (
    <PageLayout
      header={<Header />}
      footer={<Footer />}
    >
      <main>
        <h1>Page Content</h1>
        <p>Your content here...</p>
      </main>
    </PageLayout>
  );
}

// Two-column layout with sidebar
function DashboardPage() {
  return (
    <PageLayout header={<Header />}>
      <TwoColumnLayout
        sidebar={<Sidebar />}
        main={<MainContent />}
        sidebarWidth="250px"
        sidebarPosition="left"
      />
    </PageLayout>
  );
}

// Three-column layout
function DetailPage() {
  return (
    <PageLayout header={<Header />}>
      <ThreeColumnLayout
        left={<Navigation items={menuItems} />}
        main={<ResourceDetail resourceName="users" recordId="123" />}
        right={<RelatedItems />}
        leftWidth="200px"
        rightWidth="300px"
      />
    </PageLayout>
  );
}

// Responsive layout
function ResponsivePage() {
  return (
    <TwoColumnLayout
      sidebar={<Sidebar />}
      main={<MainContent />}
      sidebarWidth="300px"
      // Sidebar collapses on mobile
    />
  );
}
```

### Complete Application Example

```tsx
import React from 'react';
import { Routes, Route } from 'react-router-dom';
import {
  PageLayout,
  TwoColumnLayout,
  Navigation,
  DataTable,
  ResourceForm,
  ResourceDetail,
} from '@emf/components';

const menuItems = [
  { id: 'dashboard', label: 'Dashboard', path: '/' },
  { id: 'users', label: 'Users', path: '/users' },
  { id: 'products', label: 'Products', path: '/products' },
];

function App() {
  return (
    <PageLayout header={<AppHeader />}>
      <TwoColumnLayout
        sidebar={<Navigation items={menuItems} orientation="vertical" />}
        main={
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/users" element={<UserList />} />
            <Route path="/users/new" element={<CreateUser />} />
            <Route path="/users/:id" element={<UserDetail />} />
            <Route path="/users/:id/edit" element={<EditUser />} />
            <Route path="/products" element={<ProductList />} />
          </Routes>
        }
      />
    </PageLayout>
  );
}

function UserList() {
  const navigate = useNavigate();
  
  return (
    <div>
      <h1>Users</h1>
      <button onClick={() => navigate('/users/new')}>Add User</button>
      <DataTable
        resourceName="users"
        columns={userColumns}
        onRowClick={(user) => navigate(`/users/${user.id}`)}
      />
    </div>
  );
}

function UserDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  
  return (
    <div>
      <button onClick={() => navigate('/users')}>Back</button>
      <button onClick={() => navigate(`/users/${id}/edit`)}>Edit</button>
      <ResourceDetail resourceName="users" recordId={id!} />
    </div>
  );
}
```

## Styling

Components use CSS classes that you can customize:

```css
/* DataTable */
.emf-data-table { }
.emf-data-table-header { }
.emf-data-table-row { }
.emf-data-table-cell { }
.emf-data-table-loading { }
.emf-data-table-error { }

/* ResourceForm */
.emf-resource-form { }
.emf-form-field { }
.emf-form-label { }
.emf-form-input { }
.emf-form-error { }

/* Navigation */
.emf-navigation { }
.emf-nav-item { }
.emf-nav-item-active { }
.emf-nav-submenu { }

/* Layout */
.emf-page-layout { }
.emf-two-column-layout { }
.emf-three-column-layout { }
```
