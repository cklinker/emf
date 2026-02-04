# API Reference

Complete API reference for EMF web packages.

## @emf/sdk

### EMFClient

The main client class for interacting with EMF services.

#### Constructor

```typescript
new EMFClient(config: EMFClientConfig)
```

**EMFClientConfig:**

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `baseUrl` | `string` | Yes | Base URL of the EMF API |
| `tokenProvider` | `TokenProvider` | No | Authentication token provider |
| `cache` | `CacheConfig` | No | Cache configuration |
| `retry` | `RetryConfig` | No | Retry configuration |
| `validation` | `boolean` | No | Enable response validation (default: true) |

#### Methods

##### `discover(): Promise<ResourceMetadata[]>`

Fetches available resources from the discovery endpoint.

```typescript
const resources = await client.discover();
```

##### `resource<T>(name: string): ResourceClient<T>`

Returns a ResourceClient for the specified resource.

```typescript
const users = client.resource<User>('users');
```

##### `admin: AdminClient`

Access to administrative operations.

```typescript
const collections = await client.admin.collections.list();
```

---

### ResourceClient

Client for performing CRUD operations on a specific resource.

#### Methods

##### `list(options?: ListOptions): Promise<ListResponse<T>>`

Lists resources with optional pagination, sorting, and filtering.

**ListOptions:**

| Property | Type | Description |
|----------|------|-------------|
| `page` | `number` | Page number (1-indexed) |
| `size` | `number` | Page size |
| `sort` | `SortCriteria[]` | Sort criteria |
| `filters` | `FilterExpression[]` | Filter expressions |
| `fields` | `string[]` | Fields to include |

```typescript
const result = await users.list({
  page: 1,
  size: 20,
  sort: [{ field: 'name', direction: 'asc' }],
  filters: [{ field: 'status', operator: 'eq', value: 'active' }],
});
```

##### `get(id: string): Promise<T>`

Retrieves a single resource by ID.

```typescript
const user = await users.get('user-123');
```

##### `create(data: Partial<T>): Promise<T>`

Creates a new resource.

```typescript
const newUser = await users.create({
  name: 'John Doe',
  email: 'john@example.com',
});
```

##### `update(id: string, data: T): Promise<T>`

Fully updates a resource (PUT).

```typescript
const updated = await users.update('user-123', {
  id: 'user-123',
  name: 'John Smith',
  email: 'john.smith@example.com',
});
```

##### `patch(id: string, data: Partial<T>): Promise<T>`

Partially updates a resource (PATCH).

```typescript
const patched = await users.patch('user-123', {
  name: 'John Smith Jr.',
});
```

##### `delete(id: string): Promise<void>`

Deletes a resource.

```typescript
await users.delete('user-123');
```

##### `query(): QueryBuilder<T>`

Returns a QueryBuilder for fluent query construction.

```typescript
const results = await users.query()
  .paginate(1, 20)
  .sort('name', 'asc')
  .filter('status', 'eq', 'active')
  .execute();
```

---

### QueryBuilder

Fluent API for building complex queries.

#### Methods

##### `paginate(page: number, size: number): QueryBuilder<T>`

Sets pagination parameters.

##### `sort(field: string, direction: 'asc' | 'desc'): QueryBuilder<T>`

Adds a sort criterion. Can be called multiple times.

##### `filter(field: string, operator: FilterOperator, value: any): QueryBuilder<T>`

Adds a filter condition. Multiple filters are combined with AND.

**Filter Operators:**
- `eq` - Equals
- `ne` - Not equals
- `gt` - Greater than
- `gte` - Greater than or equal
- `lt` - Less than
- `lte` - Less than or equal
- `contains` - String contains
- `startsWith` - String starts with
- `endsWith` - String ends with
- `in` - Value in array

##### `fields(...fieldNames: string[]): QueryBuilder<T>`

Specifies which fields to include in the response.

##### `execute(): Promise<ListResponse<T>>`

Executes the query and returns results.

---

### AdminClient

Client for control plane administrative operations.

#### Collections

```typescript
client.admin.collections.list(): Promise<CollectionDefinition[]>
client.admin.collections.get(name: string): Promise<CollectionDefinition>
client.admin.collections.create(definition: CollectionDefinition): Promise<CollectionDefinition>
client.admin.collections.update(name: string, definition: CollectionDefinition): Promise<CollectionDefinition>
client.admin.collections.delete(name: string): Promise<void>
```

#### Fields

```typescript
client.admin.fields.add(collectionName: string, field: FieldDefinition): Promise<FieldDefinition>
client.admin.fields.update(collectionName: string, fieldName: string, field: FieldDefinition): Promise<FieldDefinition>
client.admin.fields.delete(collectionName: string, fieldName: string): Promise<void>
```

#### Authorization

```typescript
client.admin.authz.listRoles(): Promise<Role[]>
client.admin.authz.createRole(role: Role): Promise<Role>
client.admin.authz.updateRole(id: string, role: Role): Promise<Role>
client.admin.authz.deleteRole(id: string): Promise<void>
client.admin.authz.listPolicies(): Promise<Policy[]>
client.admin.authz.createPolicy(policy: Policy): Promise<Policy>
client.admin.authz.updatePolicy(id: string, policy: Policy): Promise<Policy>
client.admin.authz.deletePolicy(id: string): Promise<void>
```

#### OIDC Providers

```typescript
client.admin.oidc.list(): Promise<OIDCProvider[]>
client.admin.oidc.get(id: string): Promise<OIDCProvider>
client.admin.oidc.create(provider: OIDCProvider): Promise<OIDCProvider>
client.admin.oidc.update(id: string, provider: OIDCProvider): Promise<OIDCProvider>
client.admin.oidc.delete(id: string): Promise<void>
```

#### UI Configuration

```typescript
client.admin.ui.getConfig(): Promise<UIConfig>
client.admin.ui.updateConfig(config: UIConfig): Promise<UIConfig>
```

#### Packages

```typescript
client.admin.packages.export(options: ExportOptions): Promise<PackageData>
client.admin.packages.import(packageData: PackageData): Promise<ImportResult>
```

#### Migrations

```typescript
client.admin.migrations.list(): Promise<Migration[]>
client.admin.migrations.run(id: string): Promise<MigrationResult>
client.admin.migrations.rollback(id: string): Promise<MigrationResult>
```

---

### Error Classes

#### EMFError

Base error class for all SDK errors.

```typescript
class EMFError extends Error {
  statusCode?: number;
  details?: any;
}
```

#### ValidationError

Thrown when API returns 400 status.

```typescript
class ValidationError extends EMFError {
  fieldErrors: Record<string, string[]>;
}
```

#### AuthenticationError

Thrown when API returns 401 status.

#### AuthorizationError

Thrown when API returns 403 status.

#### NotFoundError

Thrown when API returns 404 status.

```typescript
class NotFoundError extends EMFError {
  resource: string;
  id: string;
}
```

#### ServerError

Thrown when API returns 5xx status.

#### NetworkError

Thrown when a network error occurs.

---

### TokenManager

Manages authentication tokens with automatic refresh.

```typescript
class TokenManager {
  constructor(provider: TokenProvider);
  getValidToken(): Promise<string | null>;
}
```

---

## @emf/components

### DataTable

Configurable data table with EMF integration.

```tsx
<DataTable<T>
  resourceName="users"
  columns={columns}
  filters={filters}
  defaultSort={[{ field: 'name', direction: 'asc' }]}
  pageSize={20}
  onRowClick={(row) => {}}
  selectable
  onSelectionChange={(selected) => {}}
/>
```

**Props:**

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `resourceName` | `string` | Yes | Name of the resource |
| `columns` | `ColumnDefinition<T>[]` | Yes | Column configuration |
| `filters` | `FilterExpression[]` | No | Active filters |
| `defaultSort` | `SortCriteria[]` | No | Default sort order |
| `pageSize` | `number` | No | Items per page (default: 10) |
| `onRowClick` | `(row: T) => void` | No | Row click handler |
| `selectable` | `boolean` | No | Enable row selection |
| `onSelectionChange` | `(selected: T[]) => void` | No | Selection change handler |

---

### ResourceForm

Form for creating and editing resources.

```tsx
<ResourceForm
  resourceName="users"
  recordId="user-123"
  onSave={(data) => {}}
  onCancel={() => {}}
  initialValues={{}}
  readOnly={false}
/>
```

**Props:**

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `resourceName` | `string` | Yes | Name of the resource |
| `recordId` | `string` | No | ID for edit mode |
| `onSave` | `(data: any) => void` | Yes | Save callback |
| `onCancel` | `() => void` | Yes | Cancel callback |
| `initialValues` | `Record<string, any>` | No | Initial form values |
| `readOnly` | `boolean` | No | Read-only mode |

---

### ResourceDetail

Display resource details in read-only mode.

```tsx
<ResourceDetail
  resourceName="users"
  recordId="user-123"
  layout="vertical"
  customRenderers={{
    avatar: (value) => <img src={value} />,
  }}
/>
```

**Props:**

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `resourceName` | `string` | Yes | Name of the resource |
| `recordId` | `string` | Yes | Resource ID |
| `layout` | `'vertical' \| 'horizontal' \| 'grid'` | No | Layout style |
| `customRenderers` | `Record<string, FieldRenderer>` | No | Custom field renderers |

---

### FilterBuilder

UI for building filter expressions.

```tsx
<FilterBuilder
  fields={fieldDefinitions}
  value={filters}
  onChange={(filters) => {}}
  maxFilters={10}
/>
```

**Props:**

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `fields` | `FieldDefinition[]` | Yes | Available fields |
| `value` | `FilterExpression[]` | Yes | Current filters |
| `onChange` | `(filters: FilterExpression[]) => void` | Yes | Change handler |
| `maxFilters` | `number` | No | Maximum number of filters |

---

### Navigation

Navigation menu component.

```tsx
<Navigation
  items={menuItems}
  currentUser={user}
  orientation="vertical"
  collapsible
/>
```

**Props:**

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `items` | `MenuItem[]` | Yes | Menu items |
| `currentUser` | `User` | No | Current user for role filtering |
| `orientation` | `'horizontal' \| 'vertical'` | No | Menu orientation |
| `collapsible` | `boolean` | No | Enable collapse |

---

### Layout Components

#### PageLayout

```tsx
<PageLayout header={<Header />} footer={<Footer />}>
  {children}
</PageLayout>
```

#### TwoColumnLayout

```tsx
<TwoColumnLayout
  sidebar={<Sidebar />}
  main={<MainContent />}
  sidebarWidth="250px"
  sidebarPosition="left"
/>
```

#### ThreeColumnLayout

```tsx
<ThreeColumnLayout
  left={<LeftPanel />}
  main={<MainContent />}
  right={<RightPanel />}
  leftWidth="200px"
  rightWidth="300px"
/>
```

---

## @emf/plugin-sdk

### Plugin Interface

```typescript
interface Plugin {
  name: string;
  version: string;
  init(context: PluginContext): void | Promise<void>;
  mount(container: HTMLElement): void | Promise<void>;
  unmount(): void | Promise<void>;
}
```

### PluginContext

```typescript
interface PluginContext {
  client: EMFClient;
  user: User | null;
  router: Router;
}
```

### BasePlugin

Abstract base class for plugins.

```typescript
abstract class BasePlugin implements Plugin {
  abstract name: string;
  abstract version: string;
  protected context?: PluginContext;
  
  init(context: PluginContext): void;
  abstract mount(container: HTMLElement): void | Promise<void>;
  abstract unmount(): void | Promise<void>;
  
  protected getClient(): EMFClient;
  protected getUser(): User | null;
  protected getRouter(): Router;
}
```

### ComponentRegistry

Registry for custom field renderers and page components.

```typescript
class ComponentRegistry {
  static registerFieldRenderer(fieldType: string, renderer: FieldRendererComponent): void;
  static getFieldRenderer(fieldType: string): FieldRendererComponent | undefined;
  static hasFieldRenderer(fieldType: string): boolean;
  static getFieldRendererTypes(): string[];
  
  static registerPageComponent(name: string, route: string, component: PageComponent): void;
  static getPageComponent(name: string): RegisteredPageComponent | undefined;
  static hasPageComponent(name: string): boolean;
  static getAllPageComponents(): RegisteredPageComponent[];
  
  static clearFieldRenderers(): void;
  static clearPageComponents(): void;
  static clearAll(): void;
}
```

### FieldRendererProps

```typescript
interface FieldRendererProps {
  value: unknown;
  field: FieldDefinition;
  onChange?: (value: unknown) => void;
  readOnly?: boolean;
}
```

### PageComponentProps

```typescript
interface PageComponentProps {
  client: EMFClient;
  params: Record<string, string>;
  user: User | null;
}
```

---

## Types

### ResourceMetadata

```typescript
interface ResourceMetadata {
  name: string;
  displayName: string;
  fields: FieldDefinition[];
  operations: string[];
  authz?: AuthzConfig;
}
```

### FieldDefinition

```typescript
interface FieldDefinition {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference';
  displayName?: string;
  required?: boolean;
  unique?: boolean;
  validation?: ValidationRule[];
  defaultValue?: unknown;
  referenceTarget?: string;
}
```

### User

```typescript
interface User {
  id: string;
  username: string;
  email?: string;
  roles: string[];
  attributes?: Record<string, unknown>;
}
```

### ListResponse

```typescript
interface ListResponse<T> {
  data: T[];
  pagination: {
    page: number;
    size: number;
    total: number;
    totalPages: number;
  };
}
```
