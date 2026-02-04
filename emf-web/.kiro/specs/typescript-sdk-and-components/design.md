# Design Document: TypeScript SDK and React Components

## Overview

The TypeScript SDK and React Components feature provides a comprehensive client-side development toolkit for EMF through three npm packages:

1. **@emf/sdk** - Type-safe TypeScript client for EMF APIs
2. **@emf/components** - Reusable React component library
3. **@emf/plugin-sdk** - Plugin development toolkit

This design leverages modern TypeScript and React patterns to provide developers with a productive, type-safe development experience. The SDK uses Axios for HTTP communication, Zod for runtime validation, and TanStack Query for data fetching and caching. Components are built with React 18+ and designed to be composable, accessible, and performant.

## Architecture

### Package Structure

```
emf-web/
├── packages/
│   ├── sdk/
│   │   ├── src/
│   │   │   ├── client/          # EMF client implementation
│   │   │   ├── resources/       # Resource operations
│   │   │   ├── query/           # Query builder
│   │   │   ├── admin/           # Control plane operations
│   │   │   ├── auth/            # Authentication
│   │   │   ├── errors/          # Error classes
│   │   │   ├── validation/      # Zod schemas
│   │   │   └── index.ts
│   │   ├── package.json
│   │   └── tsconfig.json
│   ├── components/
│   │   ├── src/
│   │   │   ├── DataTable/
│   │   │   ├── ResourceForm/
│   │   │   ├── ResourceDetail/
│   │   │   ├── FilterBuilder/
│   │   │   ├── Navigation/
│   │   │   ├── Layout/
│   │   │   └── index.ts
│   │   ├── package.json
│   │   └── tsconfig.json
│   └── plugin-sdk/
│       ├── src/
│       │   ├── plugin/          # Plugin interface
│       │   ├── registry/        # Component registry
│       │   └── index.ts
│       ├── package.json
│       └── tsconfig.json
```

### Dependency Graph

```mermaid
graph TD
    A[@emf/plugin-sdk] --> B[@emf/sdk]
    C[@emf/components] --> B
    C --> D[React 18+]
    C --> E[TanStack Query]
    B --> F[Axios]
    B --> G[Zod]
    H[emf-ui] --> A
    H --> C
    I[Custom Domain UIs] --> A
    I --> C
```

### Technology Stack

- **TypeScript 5+**: Type safety and modern language features
- **React 18+**: UI component library
- **React Router 6+**: Client-side routing
- **Axios**: HTTP client with interceptors
- **Zod**: Runtime schema validation
- **TanStack Query (React Query)**: Data fetching, caching, and state management
- **Vite**: Build tooling and bundling
- **Vitest**: Unit and integration testing
- **React Testing Library**: Component testing

## Components and Interfaces

### @emf/sdk Package

#### EMFClient Class

The main entry point for interacting with EMF services.

```typescript
interface EMFClientConfig {
  baseUrl: string;
  tokenProvider?: TokenProvider;
  cache?: CacheConfig;
  retry?: RetryConfig;
  validation?: boolean;
}

interface TokenProvider {
  getToken(): Promise<string | null>;
  refreshToken?(): Promise<string>;
}

interface CacheConfig {
  discoveryTTL?: number; // milliseconds
}

interface RetryConfig {
  maxAttempts?: number;
  backoffMultiplier?: number;
  maxDelay?: number;
}

class EMFClient {
  constructor(config: EMFClientConfig);
  
  // Resource discovery
  discover(): Promise<ResourceMetadata[]>;
  
  // Resource operations
  resource(name: string): ResourceClient;
  
  // Control plane operations
  admin: AdminClient;
}
```

#### ResourceClient Class

Provides CRUD operations for a specific resource.

```typescript
interface ListOptions {
  page?: number;
  size?: number;
  sort?: SortCriteria[];
  filters?: FilterExpression[];
  fields?: string[];
}

interface SortCriteria {
  field: string;
  direction: 'asc' | 'desc';
}

interface FilterExpression {
  field: string;
  operator: 'eq' | 'ne' | 'gt' | 'gte' | 'lt' | 'lte' | 'contains' | 'startsWith' | 'endsWith' | 'in';
  value: any;
}

interface ListResponse<T> {
  data: T[];
  pagination: {
    page: number;
    size: number;
    total: number;
    totalPages: number;
  };
}

class ResourceClient<T = any> {
  constructor(client: EMFClient, resourceName: string);
  
  list(options?: ListOptions): Promise<ListResponse<T>>;
  get(id: string): Promise<T>;
  create(data: Partial<T>): Promise<T>;
  update(id: string, data: T): Promise<T>;
  patch(id: string, data: Partial<T>): Promise<T>;
  delete(id: string): Promise<void>;
  
  // Query builder
  query(): QueryBuilder<T>;
}
```

#### QueryBuilder Class

Fluent API for constructing queries.

```typescript
class QueryBuilder<T> {
  paginate(page: number, size: number): QueryBuilder<T>;
  sort(field: string, direction: 'asc' | 'desc'): QueryBuilder<T>;
  filter(field: string, operator: string, value: any): QueryBuilder<T>;
  fields(...fieldNames: string[]): QueryBuilder<T>;
  execute(): Promise<ListResponse<T>>;
}
```

#### AdminClient Class

Control plane administrative operations.

```typescript
interface CollectionDefinition {
  name: string;
  displayName: string;
  fields: FieldDefinition[];
  authz?: AuthzConfig;
}

interface FieldDefinition {
  name: string;
  type: string;
  required?: boolean;
  unique?: boolean;
  validation?: any;
}

class AdminClient {
  // Collection management
  collections: {
    list(): Promise<CollectionDefinition[]>;
    get(name: string): Promise<CollectionDefinition>;
    create(definition: CollectionDefinition): Promise<CollectionDefinition>;
    update(name: string, definition: CollectionDefinition): Promise<CollectionDefinition>;
    delete(name: string): Promise<void>;
  };
  
  // Field management
  fields: {
    add(collectionName: string, field: FieldDefinition): Promise<FieldDefinition>;
    update(collectionName: string, fieldName: string, field: FieldDefinition): Promise<FieldDefinition>;
    delete(collectionName: string, fieldName: string): Promise<void>;
  };
  
  // Authorization management
  authz: {
    listRoles(): Promise<Role[]>;
    createRole(role: Role): Promise<Role>;
    updateRole(id: string, role: Role): Promise<Role>;
    deleteRole(id: string): Promise<void>;
    listPolicies(): Promise<Policy[]>;
    createPolicy(policy: Policy): Promise<Policy>;
    updatePolicy(id: string, policy: Policy): Promise<Policy>;
    deletePolicy(id: string): Promise<void>;
  };
  
  // OIDC provider management
  oidc: {
    list(): Promise<OIDCProvider[]>;
    get(id: string): Promise<OIDCProvider>;
    create(provider: OIDCProvider): Promise<OIDCProvider>;
    update(id: string, provider: OIDCProvider): Promise<OIDCProvider>;
    delete(id: string): Promise<void>;
  };
  
  // UI configuration
  ui: {
    getConfig(): Promise<UIConfig>;
    updateConfig(config: UIConfig): Promise<UIConfig>;
  };
  
  // Package management
  packages: {
    export(options: ExportOptions): Promise<PackageData>;
    import(packageData: PackageData): Promise<ImportResult>;
  };
  
  // Migration management
  migrations: {
    list(): Promise<Migration[]>;
    run(id: string): Promise<MigrationResult>;
    rollback(id: string): Promise<MigrationResult>;
  };
}
```

#### Error Classes

Typed error hierarchy for different failure scenarios.

```typescript
class EMFError extends Error {
  constructor(message: string, public statusCode?: number, public details?: any);
}

class ValidationError extends EMFError {
  constructor(message: string, public fieldErrors: Record<string, string[]>);
}

class AuthenticationError extends EMFError {
  constructor(message: string = 'Authentication failed');
}

class AuthorizationError extends EMFError {
  constructor(message: string = 'Access denied');
}

class NotFoundError extends EMFError {
  constructor(resource: string, id: string);
}

class ServerError extends EMFError {
  constructor(message: string, statusCode: number);
}

class NetworkError extends EMFError {
  constructor(message: string, public originalError: Error);
}
```

#### Authentication Module

Token management and automatic refresh.

```typescript
interface TokenState {
  token: string | null;
  expiresAt: number | null;
}

class TokenManager {
  constructor(private provider: TokenProvider);
  
  async getValidToken(): Promise<string | null>;
  private async refreshIfNeeded(): Promise<void>;
  private isExpired(): boolean;
}
```

#### Validation Module

Zod schemas for runtime validation.

```typescript
import { z } from 'zod';

// Resource metadata schema
const ResourceMetadataSchema = z.object({
  name: z.string(),
  displayName: z.string(),
  fields: z.array(z.object({
    name: z.string(),
    type: z.string(),
    required: z.boolean().optional(),
    unique: z.boolean().optional(),
  })),
  operations: z.array(z.string()),
  authz: z.any().optional(),
});

// List response schema
const ListResponseSchema = <T>(itemSchema: z.ZodType<T>) => z.object({
  data: z.array(itemSchema),
  pagination: z.object({
    page: z.number(),
    size: z.number(),
    total: z.number(),
    totalPages: z.number(),
  }),
});

// Error response schema
const ErrorResponseSchema = z.object({
  error: z.string(),
  message: z.string(),
  details: z.any().optional(),
  fieldErrors: z.record(z.array(z.string())).optional(),
});
```

### @emf/components Package

#### DataTable Component

Configurable data table with EMF integration.

```typescript
interface DataTableProps<T = any> {
  resourceName: string;
  columns: ColumnDefinition<T>[];
  filters?: FilterExpression[];
  defaultSort?: SortCriteria[];
  pageSize?: number;
  onRowClick?: (row: T) => void;
  selectable?: boolean;
  onSelectionChange?: (selected: T[]) => void;
}

interface ColumnDefinition<T> {
  field: keyof T;
  header: string;
  sortable?: boolean;
  filterable?: boolean;
  render?: (value: any, row: T) => React.ReactNode;
  width?: string;
}

function DataTable<T>(props: DataTableProps<T>): JSX.Element;
```

**Implementation Details:**
- Uses TanStack Query for data fetching and caching
- Supports server-side pagination, sorting, and filtering
- Provides loading and error states
- Supports column resizing and reordering
- Keyboard navigation support for accessibility

#### ResourceForm Component

Form for creating and updating resources.

```typescript
interface ResourceFormProps {
  resourceName: string;
  recordId?: string;
  onSave: (data: any) => void;
  onCancel: () => void;
  initialValues?: Record<string, any>;
  readOnly?: boolean;
}

function ResourceForm(props: ResourceFormProps): JSX.Element;
```

**Implementation Details:**
- Fetches collection schema to generate form fields
- Uses React Hook Form for form state management
- Validates against schema using Zod
- Supports field-level authorization (hides/disables restricted fields)
- Provides custom field renderers via plugin registry
- Handles optimistic updates with TanStack Query

#### ResourceDetail Component

Display resource details in read-only mode.

```typescript
interface ResourceDetailProps {
  resourceName: string;
  recordId: string;
  layout?: 'vertical' | 'horizontal' | 'grid';
  customRenderers?: Record<string, FieldRenderer>;
}

type FieldRenderer = (value: any, field: FieldDefinition) => React.ReactNode;

function ResourceDetail(props: ResourceDetailProps): JSX.Element;
```

**Implementation Details:**
- Fetches resource data and schema
- Renders fields based on type (string, number, date, boolean, etc.)
- Supports custom field renderers from plugin registry
- Respects field-level authorization
- Provides multiple layout options

#### FilterBuilder Component

UI for building filter expressions.

```typescript
interface FilterBuilderProps {
  fields: FieldDefinition[];
  value: FilterExpression[];
  onChange: (filters: FilterExpression[]) => void;
  maxFilters?: number;
}

function FilterBuilder(props: FilterBuilderProps): JSX.Element;
```

**Implementation Details:**
- Displays available fields from schema
- Shows appropriate operators based on field type
- Provides type-appropriate value inputs (text, number, date picker, etc.)
- Supports adding/removing multiple filters
- Validates filter expressions before calling onChange

#### Navigation Component

Top menu and side navigation.

```typescript
interface NavigationProps {
  items: MenuItem[];
  currentUser?: User;
  orientation?: 'horizontal' | 'vertical';
  collapsible?: boolean;
}

interface MenuItem {
  id: string;
  label: string;
  path?: string;
  icon?: React.ReactNode;
  children?: MenuItem[];
  roles?: string[];
  onClick?: () => void;
}

function Navigation(props: NavigationProps): JSX.Element;
```

**Implementation Details:**
- Integrates with React Router for navigation
- Filters menu items based on user roles
- Highlights active menu item based on current route
- Supports nested menus with expand/collapse
- Keyboard navigation support

#### Layout Components

Page layout components for consistent structure.

```typescript
interface PageLayoutProps {
  children: React.ReactNode;
  header?: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
}

function PageLayout(props: PageLayoutProps): JSX.Element;

interface TwoColumnLayoutProps {
  sidebar: React.ReactNode;
  main: React.ReactNode;
  sidebarWidth?: string;
  sidebarPosition?: 'left' | 'right';
}

function TwoColumnLayout(props: TwoColumnLayoutProps): JSX.Element;

interface ThreeColumnLayoutProps {
  left: React.ReactNode;
  main: React.ReactNode;
  right: React.ReactNode;
  leftWidth?: string;
  rightWidth?: string;
}

function ThreeColumnLayout(props: ThreeColumnLayoutProps): JSX.Element;
```

**Implementation Details:**
- Responsive layouts that adapt to screen size
- Support for custom breakpoints
- Collapsible sidebars on mobile
- Flexbox-based layout system

### @emf/plugin-sdk Package

#### Plugin Interface

Base interface for creating plugins.

```typescript
interface PluginContext {
  client: EMFClient;
  user: User | null;
  router: Router;
}

interface Plugin {
  name: string;
  version: string;
  
  init(context: PluginContext): void | Promise<void>;
  mount(container: HTMLElement): void | Promise<void>;
  unmount(): void | Promise<void>;
}

abstract class BasePlugin implements Plugin {
  abstract name: string;
  abstract version: string;
  
  protected context?: PluginContext;
  
  init(context: PluginContext): void {
    this.context = context;
  }
  
  abstract mount(container: HTMLElement): void | Promise<void>;
  abstract unmount(): void | Promise<void>;
}
```

#### Component Registry

Registry for custom field renderers and page components.

```typescript
interface FieldRendererComponent {
  (props: FieldRendererProps): JSX.Element;
}

interface FieldRendererProps {
  value: any;
  field: FieldDefinition;
  onChange?: (value: any) => void;
  readOnly?: boolean;
}

interface PageComponent {
  (props: PageComponentProps): JSX.Element;
}

interface PageComponentProps {
  client: EMFClient;
  params: Record<string, string>;
  user: User | null;
}

class ComponentRegistry {
  private static fieldRenderers = new Map<string, FieldRendererComponent>();
  private static pageComponents = new Map<string, { component: PageComponent; route: string }>();
  
  static registerFieldRenderer(fieldType: string, renderer: FieldRendererComponent): void;
  static getFieldRenderer(fieldType: string): FieldRendererComponent | undefined;
  
  static registerPageComponent(name: string, route: string, component: PageComponent): void;
  static getPageComponent(name: string): { component: PageComponent; route: string } | undefined;
  static getAllPageComponents(): Array<{ name: string; route: string; component: PageComponent }>;
}
```

**Implementation Details:**
- Singleton registry pattern
- Type-safe registration and retrieval
- Last-registered-wins for duplicate registrations
- Used by ResourceForm, ResourceDetail, and router configuration

## Data Models

### Core Types

```typescript
// Resource metadata from discovery
interface ResourceMetadata {
  name: string;
  displayName: string;
  fields: FieldDefinition[];
  operations: string[];
  authz?: AuthzConfig;
}

// Field definition
interface FieldDefinition {
  name: string;
  type: 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'json' | 'reference';
  displayName?: string;
  required?: boolean;
  unique?: boolean;
  validation?: ValidationRule[];
  defaultValue?: any;
  referenceTarget?: string; // For reference fields
}

// Validation rule
interface ValidationRule {
  type: 'min' | 'max' | 'pattern' | 'email' | 'url' | 'custom';
  value?: any;
  message?: string;
}

// Authorization configuration
interface AuthzConfig {
  read?: string[]; // Role names
  create?: string[];
  update?: string[];
  delete?: string[];
  fieldLevel?: Record<string, string[]>;
}
```

### User and Authentication Types

```typescript
interface User {
  id: string;
  username: string;
  email?: string;
  roles: string[];
  attributes?: Record<string, any>;
}

interface Role {
  id: string;
  name: string;
  description?: string;
  permissions: string[];
}

interface Policy {
  id: string;
  name: string;
  resource: string;
  actions: string[];
  effect: 'allow' | 'deny';
  conditions?: PolicyCondition[];
}

interface PolicyCondition {
  field: string;
  operator: string;
  value: any;
}
```

### Control Plane Types

```typescript
interface OIDCProvider {
  id: string;
  name: string;
  issuer: string;
  clientId: string;
  clientSecret: string;
  scopes: string[];
  enabled: boolean;
}

interface UIConfig {
  theme?: ThemeConfig;
  branding?: BrandingConfig;
  features?: Record<string, boolean>;
}

interface ThemeConfig {
  primaryColor?: string;
  secondaryColor?: string;
  fontFamily?: string;
}

interface BrandingConfig {
  logo?: string;
  title?: string;
  favicon?: string;
}

interface PackageData {
  version: string;
  collections: CollectionDefinition[];
  roles: Role[];
  policies: Policy[];
  uiConfig?: UIConfig;
}

interface ExportOptions {
  includeCollections?: string[];
  includeRoles?: boolean;
  includePolicies?: boolean;
  includeUIConfig?: boolean;
}

interface ImportResult {
  success: boolean;
  imported: {
    collections: number;
    roles: number;
    policies: number;
  };
  errors?: string[];
}

interface Migration {
  id: string;
  name: string;
  description?: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  createdAt: string;
  completedAt?: string;
}

interface MigrationResult {
  success: boolean;
  message: string;
  details?: any;
}
```

## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property Reflection

After analyzing all acceptance criteria, I identified several areas of redundancy:

1. **Token Provider Behavior (1.2, 1.3, 9.5)**: Requirements 1.3 and 9.5 are redundant with 1.2 - they test the absence of a token provider, which is already covered by testing token provider presence/absence.

2. **Query Parameter Construction (3.2-3.5)**: These can be combined into a single comprehensive property about query parameter construction from options.

3. **CRUD Operations (4.1-4.5)**: These follow the same pattern and can be tested with a single property about HTTP method and URL construction.

4. **Error Status Code Mapping (8.1-8.6)**: These are specific examples of error mapping and should be tested as examples rather than separate properties.

5. **Component Initialization**: Many component requirements (11.1, 12.1, 13.1) follow the same pattern of fetching data on mount - these are examples rather than properties.

### SDK Properties

**Property 1: Base URL propagation**
*For any* valid base URL and API endpoint, when a request is made through the EMF_Client, the full URL should be constructed by combining the base URL with the endpoint path.
**Validates: Requirements 1.1**

**Property 2: Token provider integration**
*For any* EMF_Client configuration, when a Token_Provider is present, all requests should include the Authorization header with the token; when absent, requests should not include the Authorization header.
**Validates: Requirements 1.2, 1.3, 9.5**

**Property 3: HTTP client options propagation**
*For any* custom HTTP client options provided during initialization, all requests made by the EMF_Client should apply those options.
**Validates: Requirements 1.4**

**Property 4: Discovery response parsing**
*For any* valid discovery response containing N resources, the EMF_Client should return exactly N Collection_Schema objects with all fields preserved.
**Validates: Requirements 2.2**

**Property 5: Discovery cache behavior**
*For any* sequence of discover calls within the TTL period, only the first call should result in a network request; subsequent calls should return cached data.
**Validates: Requirements 2.3**

**Property 6: Error response mapping**
*For any* API error response with a status code, the EMF_Client should throw an error of the appropriate type (ValidationError for 400, AuthenticationError for 401, etc.) with the response details preserved.
**Validates: Requirements 2.5, 4.7, 8.1-8.6**

**Property 7: Resource URL construction**
*For any* resource name and operation (list, get, create, update, patch, delete), the EMF_Client should construct the correct URL path following the pattern /api/{resourceName} or /api/{resourceName}/{id}.
**Validates: Requirements 3.1, 4.1-4.5**

**Property 8: Query parameter construction**
*For any* ListOptions containing pagination, sorting, filtering, or field selection, the EMF_Client should include all specified options as query parameters in the request URL.
**Validates: Requirements 3.2, 3.3, 3.4, 3.5**

**Property 9: Response validation**
*For any* API response, when validation is enabled, the EMF_Client should validate the response against the expected schema and throw a ValidationError if the response doesn't match.
**Validates: Requirements 3.6, 10.1, 10.3**

**Property 10: List response structure**
*For any* valid list response, the EMF_Client should return an object containing a data array and pagination metadata with page, size, total, and totalPages fields.
**Validates: Requirements 3.7**

**Property 11: CRUD operation HTTP methods**
*For any* CRUD operation, the EMF_Client should use the correct HTTP method: GET for list/get, POST for create, PUT for update, PATCH for patch, DELETE for delete.
**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

**Property 12: CRUD operation payloads**
*For any* create, update, or patch operation with data, the EMF_Client should include the data in the request body.
**Validates: Requirements 4.2, 4.3, 4.4**

**Property 13: Successful operation response**
*For any* successful CRUD operation, the EMF_Client should return the validated response data.
**Validates: Requirements 4.6**

**Property 14: Query builder state accumulation**
*For any* sequence of Query_Builder method calls (paginate, sort, filter, fields), each call should accumulate state without overwriting previous calls, and execute should include all accumulated parameters.
**Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**

**Property 15: Multiple filter combination**
*For any* Query_Builder with multiple filter calls, the filters should be combined with AND logic in the resulting query parameters.
**Validates: Requirements 5.6**

**Property 16: Sort order preservation**
*For any* Query_Builder with multiple sort calls, the sort criteria should be applied in the order they were added.
**Validates: Requirements 5.7**

**Property 17: Admin endpoint construction**
*For any* admin operation (collections, fields, authz, oidc, ui, packages, migrations), the EMF_Client should construct URLs following the pattern /api/_admin/{category} or /api/_admin/{category}/{id}.
**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**

**Property 18: Type generation from OpenAPI**
*For any* valid OpenAPI specification containing collection schemas, the type generation CLI should produce TypeScript interfaces for each collection with all fields and types correctly mapped.
**Validates: Requirements 7.2, 7.3, 7.4**

**Property 19: Invalid OpenAPI error reporting**
*For any* invalid OpenAPI specification, the type generation CLI should report validation errors without generating types.
**Validates: Requirements 7.6**

**Property 20: Retry with exponential backoff**
*For any* retryable error, the EMF_Client should retry the request up to the maximum number of attempts with exponentially increasing delays between attempts.
**Validates: Requirements 8.7**

**Property 21: Token provider invocation**
*For any* request made by an EMF_Client with a configured Token_Provider, the provider's getToken method should be called before the request.
**Validates: Requirements 9.1**

**Property 22: Token header injection**
*For any* non-null token returned by a Token_Provider, the EMF_Client should include it in the Authorization header as "Bearer {token}".
**Validates: Requirements 9.2**

**Property 23: Token refresh on expiration**
*For any* expired token, the Token_Provider should call its refresh method before returning the token.
**Validates: Requirements 9.3**

**Property 24: Validation bypass**
*For any* EMF_Client with validation disabled, API responses should be returned without validation, preserving the raw response data.
**Validates: Requirements 10.4**

### Component Properties

**Property 25: DataTable column rendering order**
*For any* column configuration array, the DataTable should render columns in the exact order specified in the array.
**Validates: Requirements 11.2**

**Property 26: ResourceForm field generation**
*For any* Collection_Schema with N fields, the ResourceForm should generate exactly N form fields with types matching the schema field types.
**Validates: Requirements 12.2**

**Property 27: ResourceForm operation selection**
*For any* ResourceForm, when recordId is provided, submitting should call update; when recordId is absent, submitting should call create.
**Validates: Requirements 12.5**

**Property 28: ResourceForm field authorization**
*For any* field with authorization restrictions, the ResourceForm should disable or hide that field when the current user lacks the required role.
**Validates: Requirements 12.8**

**Property 29: ResourceDetail field rendering**
*For any* Collection_Schema and resource data, the ResourceDetail should render all fields present in the schema using the appropriate renderer for each field type.
**Validates: Requirements 13.2**

**Property 30: ResourceDetail field authorization**
*For any* field with authorization restrictions, the ResourceDetail should hide that field when the current user lacks the required role.
**Validates: Requirements 13.5**

**Property 31: FilterBuilder field display**
*For any* array of field definitions, the FilterBuilder should display all fields as available for filtering.
**Validates: Requirements 14.1**

**Property 32: FilterBuilder operator filtering**
*For any* field type, the FilterBuilder should show only operators appropriate for that type (e.g., string fields show contains/startsWith/endsWith, number fields show greaterThan/lessThan/between).
**Validates: Requirements 14.3, 14.6, 14.7**

**Property 33: FilterBuilder change callback**
*For any* change to the filter list (add, remove, or modify), the FilterBuilder should call the onFilterChange callback with the complete updated filter array.
**Validates: Requirements 14.5**

**Property 34: Navigation menu rendering**
*For any* menu configuration with N items, the Navigation should render exactly N visible items (excluding items filtered by role restrictions).
**Validates: Requirements 15.1**

**Property 35: Navigation role-based filtering**
*For any* menu item with role restrictions, the Navigation should hide that item when the current user's roles don't include any of the required roles.
**Validates: Requirements 15.2**

**Property 36: Navigation active state**
*For any* current route, the Navigation should highlight exactly one menu item whose path matches the route (or no items if no match exists).
**Validates: Requirements 15.4**

**Property 37: Navigation nested menu rendering**
*For any* menu item with children, the Navigation should render a submenu containing all child items.
**Validates: Requirements 15.5**

**Property 38: Layout responsive adaptation**
*For any* layout component with responsive breakpoints, changing the viewport width across breakpoints should trigger layout restructuring.
**Validates: Requirements 16.4**

**Property 39: Layout style application**
*For any* custom styling props provided to a layout component, those styles should be applied to the rendered output.
**Validates: Requirements 16.5**

### Plugin Properties

**Property 40: Field renderer registration and retrieval**
*For any* field type and renderer, after registering the renderer for that type, retrieving the renderer for that type should return the registered renderer.
**Validates: Requirements 18.1**

**Property 41: Field renderer value display**
*For any* field value and renderer, the renderer should display the value in a way that preserves the value's information.
**Validates: Requirements 18.4**

**Property 42: Field renderer last-wins precedence**
*For any* field type with multiple renderer registrations, the last registered renderer should be the one returned by retrieval.
**Validates: Requirements 18.7**

**Property 43: Page component registration and retrieval**
*For any* page component name, route, and component, after registration, retrieving by name should return the component and route.
**Validates: Requirements 19.1**

## Error Handling

### Error Hierarchy

All errors thrown by the SDK extend from `EMFError`, which includes:
- `message`: Human-readable error description
- `statusCode`: HTTP status code (if applicable)
- `details`: Additional error context

### Error Types and Handling

1. **ValidationError (400)**
   - Thrown when API returns validation errors
   - Includes `fieldErrors` map for field-level errors
   - Used for form validation feedback

2. **AuthenticationError (401)**
   - Thrown when authentication fails or token is invalid
   - Triggers token refresh if Token_Provider supports it
   - Should prompt user to re-authenticate

3. **AuthorizationError (403)**
   - Thrown when user lacks permission for operation
   - Should display access denied message
   - May hide UI elements based on permissions

4. **NotFoundError (404)**
   - Thrown when resource or record doesn't exist
   - Includes resource name and ID in message
   - Should display "not found" message or redirect

5. **ServerError (500+)**
   - Thrown for server-side errors
   - Includes status code and error details
   - Should display generic error message and log details

6. **NetworkError**
   - Thrown for network connectivity issues
   - Includes original error for debugging
   - Should display "connection failed" message
   - Triggers retry logic if configured

### Retry Strategy

The SDK implements exponential backoff for retryable errors:
- Network errors (connection refused, timeout)
- Server errors (500, 502, 503, 504)
- Rate limiting (429)

Retry configuration:
```typescript
{
  maxAttempts: 3,
  backoffMultiplier: 2,
  maxDelay: 10000 // milliseconds
}
```

Delay calculation: `min(baseDelay * (backoffMultiplier ^ attemptNumber), maxDelay)`

Non-retryable errors (4xx except 429) fail immediately without retry.

## Testing Strategy

### Dual Testing Approach

This feature requires both unit tests and property-based tests for comprehensive coverage:

- **Unit tests**: Validate specific examples, edge cases, error conditions, and component behavior
- **Property tests**: Verify universal properties across all inputs using randomized testing

Both approaches are complementary and necessary. Unit tests catch concrete bugs and validate specific scenarios, while property tests verify general correctness across a wide range of inputs.

### Property-Based Testing

We will use **fast-check** (for TypeScript/JavaScript) as our property-based testing library. Fast-check integrates well with Vitest and provides excellent TypeScript support.

**Configuration:**
- Minimum 100 iterations per property test (due to randomization)
- Each property test must reference its design document property
- Tag format: `// Feature: typescript-sdk-and-components, Property {number}: {property_text}`

**Example Property Test:**

```typescript
import { test } from 'vitest';
import * as fc from 'fast-check';

// Feature: typescript-sdk-and-components, Property 1: Base URL propagation
test('base URL is correctly combined with endpoint paths', () => {
  fc.assert(
    fc.property(
      fc.webUrl(), // Generate random base URLs
      fc.string(), // Generate random endpoint paths
      (baseUrl, endpoint) => {
        const client = new EMFClient({ baseUrl });
        const fullUrl = client.buildUrl(endpoint);
        return fullUrl.startsWith(baseUrl);
      }
    ),
    { numRuns: 100 }
  );
});
```

### Unit Testing Strategy

**SDK Unit Tests:**
- HTTP client configuration and initialization
- URL construction for various operations
- Query parameter serialization
- Error parsing and error class instantiation
- Token management and refresh logic
- Response validation with Zod schemas
- Cache behavior with time-based expiration
- Retry logic with exponential backoff

**Component Unit Tests:**
- Component rendering with various props
- User interaction handling (clicks, form submission)
- Data fetching integration with TanStack Query
- Loading and error states
- Authorization-based field visibility
- Custom renderer registration and usage
- Callback invocation (onSave, onCancel, onChange)

**Plugin SDK Unit Tests:**
- Plugin lifecycle (init, mount, unmount)
- Component registry operations
- Context passing to plugins

### Integration Testing

Integration tests validate interactions between SDK and mock APIs:

1. **Mock Server Setup**: Use MSW (Mock Service Worker) to mock EMF API endpoints
2. **End-to-End Flows**: Test complete workflows (discover → list → create → update → delete)
3. **Component Integration**: Test components with real SDK instances against mock APIs
4. **Error Scenarios**: Test error handling across the stack

**Example Integration Test:**

```typescript
import { setupServer } from 'msw/node';
import { rest } from 'msw';

const server = setupServer(
  rest.get('/api/_meta/resources', (req, res, ctx) => {
    return res(ctx.json({ resources: [...] }));
  })
);

test('DataTable fetches and displays data', async () => {
  const client = new EMFClient({ baseUrl: 'http://localhost' });
  render(<DataTable client={client} resourceName="users" columns={[...]} />);
  
  await waitFor(() => {
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });
});
```

### Component Testing

Use React Testing Library for component tests:

- Render components with various prop combinations
- Simulate user interactions (click, type, submit)
- Assert on rendered output and DOM structure
- Verify accessibility (ARIA attributes, keyboard navigation)
- Test responsive behavior at different viewport sizes

### Test Coverage Goals

- **Overall coverage**: Minimum 80%
- **Critical paths**: 100% coverage for error handling, authentication, and authorization logic
- **Property tests**: All 43 correctness properties must have corresponding property tests
- **Edge cases**: Explicit unit tests for boundary conditions and error scenarios

### Testing Tools

- **Vitest**: Test runner and assertion library
- **fast-check**: Property-based testing
- **React Testing Library**: Component testing
- **MSW**: API mocking
- **@testing-library/user-event**: User interaction simulation
- **@vitest/coverage-v8**: Code coverage reporting

### Continuous Integration

Tests run automatically on:
- Every commit (pre-commit hook)
- Pull requests (CI pipeline)
- Before publishing packages

CI pipeline includes:
1. Lint checks (ESLint, TypeScript)
2. Unit tests
3. Property tests
4. Integration tests
5. Coverage reporting
6. Build verification

### Test Organization

```
packages/sdk/
├── src/
│   ├── client/
│   │   ├── EMFClient.ts
│   │   └── EMFClient.test.ts
│   ├── resources/
│   │   ├── ResourceClient.ts
│   │   └── ResourceClient.test.ts
│   └── ...
└── tests/
    ├── integration/
    │   ├── crud-operations.test.ts
    │   └── discovery.test.ts
    └── properties/
        ├── url-construction.property.test.ts
        └── query-builder.property.test.ts

packages/components/
├── src/
│   ├── DataTable/
│   │   ├── DataTable.tsx
│   │   ├── DataTable.test.tsx
│   │   └── DataTable.property.test.tsx
│   └── ...
└── tests/
    └── integration/
        └── form-submission.test.tsx
```

### Property Test Examples

Each correctness property should have a corresponding property test. Here are examples for key properties:

**Property 8: Query parameter construction**
```typescript
// Feature: typescript-sdk-and-components, Property 8: Query parameter construction
test('all list options are included as query parameters', () => {
  fc.assert(
    fc.property(
      fc.record({
        page: fc.option(fc.nat()),
        size: fc.option(fc.nat()),
        sort: fc.option(fc.array(fc.record({
          field: fc.string(),
          direction: fc.constantFrom('asc', 'desc')
        }))),
        filters: fc.option(fc.array(fc.record({
          field: fc.string(),
          operator: fc.constantFrom('eq', 'ne', 'gt', 'contains'),
          value: fc.anything()
        }))),
        fields: fc.option(fc.array(fc.string()))
      }),
      (options) => {
        const client = new EMFClient({ baseUrl: 'http://test' });
        const url = client.resource('test').buildListUrl(options);
        
        // Verify all provided options are in the URL
        if (options.page !== undefined) {
          expect(url).toContain(`page=${options.page}`);
        }
        if (options.size !== undefined) {
          expect(url).toContain(`size=${options.size}`);
        }
        // ... verify other options
        return true;
      }
    ),
    { numRuns: 100 }
  );
});
```

**Property 15: Multiple filter combination**
```typescript
// Feature: typescript-sdk-and-components, Property 15: Multiple filter combination
test('multiple filters are combined with AND logic', () => {
  fc.assert(
    fc.property(
      fc.array(fc.record({
        field: fc.string(),
        operator: fc.constantFrom('eq', 'ne'),
        value: fc.string()
      }), { minLength: 2, maxLength: 5 }),
      (filters) => {
        const builder = new QueryBuilder(mockClient, 'test');
        filters.forEach(f => builder.filter(f.field, f.operator, f.value));
        
        const queryParams = builder.buildQueryParams();
        // Verify all filters are present and combined with AND
        return filters.every(f => 
          queryParams.includes(`${f.field}[${f.operator}]=${f.value}`)
        );
      }
    ),
    { numRuns: 100 }
  );
});
```

**Property 26: ResourceForm field generation**
```typescript
// Feature: typescript-sdk-and-components, Property 26: ResourceForm field generation
test('form generates exactly N fields for N schema fields', () => {
  fc.assert(
    fc.property(
      fc.array(fc.record({
        name: fc.string(),
        type: fc.constantFrom('string', 'number', 'boolean', 'date'),
        required: fc.boolean()
      }), { minLength: 1, maxLength: 10 }),
      (fields) => {
        const schema = { name: 'test', fields };
        const { container } = render(
          <ResourceForm resourceName="test" schema={schema} onSave={() => {}} onCancel={() => {}} />
        );
        
        const formFields = container.querySelectorAll('[data-field]');
        return formFields.length === fields.length;
      }
    ),
    { numRuns: 100 }
  );
});
```

### Test Data Generators

Create reusable generators for common test data:

```typescript
// test-utils/generators.ts
import * as fc from 'fast-check';

export const arbResourceMetadata = fc.record({
  name: fc.string(),
  displayName: fc.string(),
  fields: fc.array(arbFieldDefinition),
  operations: fc.array(fc.constantFrom('list', 'get', 'create', 'update', 'delete'))
});

export const arbFieldDefinition = fc.record({
  name: fc.string(),
  type: fc.constantFrom('string', 'number', 'boolean', 'date', 'json'),
  required: fc.boolean(),
  unique: fc.boolean()
});

export const arbFilterExpression = fc.record({
  field: fc.string(),
  operator: fc.constantFrom('eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'contains'),
  value: fc.anything()
});
```

These generators ensure consistent test data across all property tests and make tests more maintainable.
