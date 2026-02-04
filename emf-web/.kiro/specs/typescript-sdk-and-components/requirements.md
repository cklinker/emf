# Requirements Document: TypeScript SDK and React Components

## Introduction

The TypeScript SDK and React Components feature provides a comprehensive client-side development toolkit for the EMF (Enterprise Microservice Framework). This feature enables developers to build type-safe, performant UIs that interact with EMF services through three npm packages: @emf/sdk (TypeScript client), @emf/components (React component library), and @emf/plugin-sdk (plugin development toolkit).

## Glossary

- **EMF_Client**: The main TypeScript client class for interacting with EMF services
- **Resource**: A collection exposed by the EMF control plane (e.g., users, orders)
- **Collection_Schema**: Metadata describing a resource's structure, fields, and operations
- **Query_Builder**: Fluent API for constructing resource queries with filters, sorting, and pagination
- **Control_Plane**: The EMF administrative service managing collections, authorization, and configuration
- **Discovery_Endpoint**: The /api/_meta/resources endpoint that returns available resources
- **Token_Provider**: Interface for supplying authentication tokens to the client
- **Field_Renderer**: Component responsible for displaying or editing a specific field type
- **Plugin**: Extensible module that adds custom functionality to EMF UIs
- **Resource_Operation**: CRUD operation (list, get, create, update, patch, delete) on a resource
- **Filter_Expression**: Query parameter specifying conditions for filtering results
- **Pagination_Options**: Parameters controlling page size and offset for list operations
- **Sort_Criteria**: Parameters specifying field and direction for result ordering
- **Validation_Schema**: Zod schema for runtime validation of API responses
- **Error_Response**: Structured error format returned by EMF APIs
- **Auth_Token**: JWT token used for authenticating requests
- **OpenAPI_Spec**: Machine-readable API specification at /openapi.json
- **Type_Generation**: Process of creating TypeScript interfaces from OpenAPI specifications

## Requirements

### Requirement 1: EMF Client Initialization

**User Story:** As a developer, I want to initialize an EMF client with configuration options, so that I can interact with EMF services.

#### Acceptance Criteria

1. WHEN a developer creates an EMF_Client instance with a base URL, THEN THE EMF_Client SHALL store the base URL for subsequent requests
2. WHEN a developer provides a Token_Provider during initialization, THEN THE EMF_Client SHALL use it for authentication
3. WHEN a developer creates an EMF_Client without a Token_Provider, THEN THE EMF_Client SHALL allow unauthenticated requests
4. WHEN a developer provides custom HTTP client options, THEN THE EMF_Client SHALL apply those options to all requests

### Requirement 2: Resource Discovery

**User Story:** As a developer, I want to discover available resources dynamically, so that my UI can adapt to the control plane configuration.

#### Acceptance Criteria

1. WHEN the EMF_Client calls the discover method, THEN THE EMF_Client SHALL fetch resource metadata from the Discovery_Endpoint
2. WHEN discovery succeeds, THEN THE EMF_Client SHALL return Collection_Schema objects for all available resources
3. WHEN discovery results are cached, THEN THE EMF_Client SHALL reuse cached results within the TTL period
4. WHEN the cache TTL expires, THEN THE EMF_Client SHALL fetch fresh discovery data on the next discover call
5. IF the Discovery_Endpoint returns an error, THEN THE EMF_Client SHALL throw a typed error with the response details

### Requirement 3: Collection List Operations

**User Story:** As a developer, I want to list resources with pagination, filtering, and sorting, so that I can display data efficiently.

#### Acceptance Criteria

1. WHEN the EMF_Client calls list on a resource, THEN THE EMF_Client SHALL send a GET request to /api/{resourceName}
2. WHEN Pagination_Options are provided, THEN THE EMF_Client SHALL include page and size query parameters
3. WHEN Sort_Criteria are provided, THEN THE EMF_Client SHALL include sort query parameters
4. WHEN Filter_Expression objects are provided, THEN THE EMF_Client SHALL include filter query parameters
5. WHEN field selection is specified, THEN THE EMF_Client SHALL include fields query parameters
6. WHEN the list response is received, THEN THE EMF_Client SHALL validate it against the expected schema
7. WHEN the list response is valid, THEN THE EMF_Client SHALL return typed data with pagination metadata

### Requirement 4: Collection CRUD Operations

**User Story:** As a developer, I want to perform CRUD operations on resources, so that I can manage data through the EMF API.

#### Acceptance Criteria

1. WHEN the EMF_Client calls get with a resource ID, THEN THE EMF_Client SHALL send a GET request to /api/{resourceName}/{id}
2. WHEN the EMF_Client calls create with data, THEN THE EMF_Client SHALL send a POST request to /api/{resourceName} with the data payload
3. WHEN the EMF_Client calls update with an ID and data, THEN THE EMF_Client SHALL send a PUT request to /api/{resourceName}/{id} with the data payload
4. WHEN the EMF_Client calls patch with an ID and partial data, THEN THE EMF_Client SHALL send a PATCH request to /api/{resourceName}/{id} with the partial payload
5. WHEN the EMF_Client calls delete with an ID, THEN THE EMF_Client SHALL send a DELETE request to /api/{resourceName}/{id}
6. WHEN any CRUD operation succeeds, THEN THE EMF_Client SHALL return the validated response data
7. IF any CRUD operation fails, THEN THE EMF_Client SHALL throw a typed error with the Error_Response details

### Requirement 5: Query Builder

**User Story:** As a developer, I want a fluent API for building queries, so that I can construct complex queries easily.

#### Acceptance Criteria

1. WHEN the Query_Builder calls paginate with page and size, THEN THE Query_Builder SHALL store pagination parameters
2. WHEN the Query_Builder calls sort with field and direction, THEN THE Query_Builder SHALL add sort criteria
3. WHEN the Query_Builder calls filter with field, operator, and value, THEN THE Query_Builder SHALL add a filter condition
4. WHEN the Query_Builder calls fields with field names, THEN THE Query_Builder SHALL store field selection
5. WHEN the Query_Builder calls execute, THEN THE Query_Builder SHALL construct query parameters and execute the request
6. WHEN multiple filters are added, THEN THE Query_Builder SHALL combine them with AND logic
7. WHEN multiple sort criteria are added, THEN THE Query_Builder SHALL apply them in the order specified

### Requirement 6: Control Plane Operations

**User Story:** As a developer, I want to manage control plane resources programmatically, so that I can automate administrative tasks.

#### Acceptance Criteria

1. WHEN the EMF_Client calls collection management methods, THEN THE EMF_Client SHALL send requests to /api/_admin/collections endpoints
2. WHEN the EMF_Client calls field management methods, THEN THE EMF_Client SHALL send requests to /api/_admin/collections/{name}/fields endpoints
3. WHEN the EMF_Client calls authorization management methods, THEN THE EMF_Client SHALL send requests to /api/_admin/authz endpoints
4. WHEN the EMF_Client calls OIDC provider management methods, THEN THE EMF_Client SHALL send requests to /api/_admin/oidc endpoints
5. WHEN the EMF_Client calls UI configuration methods, THEN THE EMF_Client SHALL send requests to /api/_admin/ui endpoints
6. WHEN the EMF_Client calls package import/export methods, THEN THE EMF_Client SHALL send requests to /api/_admin/packages endpoints
7. WHEN the EMF_Client calls migration methods, THEN THE EMF_Client SHALL send requests to /api/_admin/migrations endpoints

### Requirement 7: Type Generation

**User Story:** As a developer, I want to generate TypeScript types from OpenAPI specifications, so that I have compile-time type safety.

#### Acceptance Criteria

1. WHEN a developer runs the type generation CLI with an OpenAPI_Spec URL, THEN THE CLI SHALL fetch the specification
2. WHEN the OpenAPI_Spec is fetched, THEN THE CLI SHALL parse collection schemas
3. WHEN collection schemas are parsed, THEN THE CLI SHALL generate TypeScript interfaces for each collection
4. WHEN request/response types are identified, THEN THE CLI SHALL generate corresponding TypeScript types
5. WHEN type generation completes, THEN THE CLI SHALL write types to the specified output file
6. IF the OpenAPI_Spec is invalid, THEN THE CLI SHALL report validation errors

### Requirement 8: Error Handling

**User Story:** As a developer, I want structured error handling, so that I can handle different error scenarios appropriately.

#### Acceptance Criteria

1. WHEN an API request returns a 400 status, THEN THE EMF_Client SHALL throw a ValidationError with field-level errors
2. WHEN an API request returns a 401 status, THEN THE EMF_Client SHALL throw an AuthenticationError
3. WHEN an API request returns a 403 status, THEN THE EMF_Client SHALL throw an AuthorizationError
4. WHEN an API request returns a 404 status, THEN THE EMF_Client SHALL throw a NotFoundError
5. WHEN an API request returns a 500 status, THEN THE EMF_Client SHALL throw a ServerError
6. WHEN a network error occurs, THEN THE EMF_Client SHALL throw a NetworkError
7. WHEN an error is retryable, THEN THE EMF_Client SHALL retry with exponential backoff up to a maximum number of attempts

### Requirement 9: Authentication

**User Story:** As a developer, I want automatic token management, so that I don't have to manually handle authentication.

#### Acceptance Criteria

1. WHEN a Token_Provider is configured, THEN THE EMF_Client SHALL call it before each request
2. WHEN a Token_Provider returns an Auth_Token, THEN THE EMF_Client SHALL include it in the Authorization header
3. WHEN an Auth_Token is expired, THEN THE Token_Provider SHALL refresh it automatically
4. WHEN token refresh fails, THEN THE EMF_Client SHALL throw an AuthenticationError
5. WHEN no Token_Provider is configured, THEN THE EMF_Client SHALL send requests without authentication headers

### Requirement 10: Response Validation

**User Story:** As a developer, I want runtime validation of API responses, so that I can catch schema mismatches early.

#### Acceptance Criteria

1. WHEN an API response is received, THEN THE EMF_Client SHALL validate it against the expected Validation_Schema
2. WHEN validation succeeds, THEN THE EMF_Client SHALL return the typed data
3. IF validation fails, THEN THE EMF_Client SHALL throw a ValidationError with details about the mismatch
4. WHEN validation is disabled in configuration, THEN THE EMF_Client SHALL skip validation and return raw data

### Requirement 11: DataTable Component

**User Story:** As a developer, I want a configurable data table component, so that I can display resource lists with minimal code.

#### Acceptance Criteria

1. WHEN the DataTable component receives a resourceName prop, THEN THE DataTable SHALL fetch data using the EMF_Client
2. WHEN the DataTable component receives column configuration, THEN THE DataTable SHALL render columns in the specified order
3. WHEN a user clicks a column header, THEN THE DataTable SHALL sort by that column
4. WHEN a user changes the page, THEN THE DataTable SHALL fetch the new page of data
5. WHEN a user applies filters, THEN THE DataTable SHALL refetch data with the filter criteria
6. WHEN data is loading, THEN THE DataTable SHALL display a loading indicator
7. IF data fetching fails, THEN THE DataTable SHALL display an error message

### Requirement 12: ResourceForm Component

**User Story:** As a developer, I want a form component for creating and updating resources, so that I can build CRUD UIs quickly.

#### Acceptance Criteria

1. WHEN the ResourceForm component receives a resourceName prop, THEN THE ResourceForm SHALL fetch the Collection_Schema
2. WHEN the Collection_Schema is loaded, THEN THE ResourceForm SHALL generate form fields based on field definitions
3. WHEN the ResourceForm component receives a recordId prop, THEN THE ResourceForm SHALL fetch existing data and populate the form
4. WHEN a user submits the form, THEN THE ResourceForm SHALL validate data against the schema
5. WHEN validation succeeds, THEN THE ResourceForm SHALL call create or update based on whether recordId is provided
6. WHEN the save operation succeeds, THEN THE ResourceForm SHALL call the onSave callback
7. WHEN a user cancels, THEN THE ResourceForm SHALL call the onCancel callback
8. WHEN a field has authorization restrictions, THEN THE ResourceForm SHALL disable or hide that field appropriately

### Requirement 13: ResourceDetail Component

**User Story:** As a developer, I want a component for displaying resource details, so that I can show read-only views easily.

#### Acceptance Criteria

1. WHEN the ResourceDetail component receives resourceName and recordId props, THEN THE ResourceDetail SHALL fetch the resource data
2. WHEN resource data is loaded, THEN THE ResourceDetail SHALL render fields based on the Collection_Schema
3. WHEN a field has a custom Field_Renderer registered, THEN THE ResourceDetail SHALL use that renderer
4. WHEN no custom renderer exists, THEN THE ResourceDetail SHALL use the default renderer for the field type
5. WHEN a field has authorization restrictions, THEN THE ResourceDetail SHALL hide that field if the user lacks permission
6. WHEN data is loading, THEN THE ResourceDetail SHALL display a loading indicator
7. IF data fetching fails, THEN THE ResourceDetail SHALL display an error message

### Requirement 14: FilterBuilder Component

**User Story:** As a developer, I want a UI for building filter expressions, so that users can filter data interactively.

#### Acceptance Criteria

1. WHEN the FilterBuilder component receives field definitions, THEN THE FilterBuilder SHALL display available fields
2. WHEN a user adds a filter, THEN THE FilterBuilder SHALL display field, operator, and value inputs
3. WHEN a user selects a field, THEN THE FilterBuilder SHALL show operators appropriate for that field type
4. WHEN a user removes a filter, THEN THE FilterBuilder SHALL remove it from the filter list
5. WHEN filters change, THEN THE FilterBuilder SHALL call the onFilterChange callback with the updated Filter_Expression objects
6. WHEN a field type is string, THEN THE FilterBuilder SHALL offer operators: equals, contains, startsWith, endsWith
7. WHEN a field type is number, THEN THE FilterBuilder SHALL offer operators: equals, greaterThan, lessThan, between

### Requirement 15: Navigation Component

**User Story:** As a developer, I want navigation components, so that I can build consistent navigation UIs.

#### Acceptance Criteria

1. WHEN the Navigation component receives a menu configuration, THEN THE Navigation SHALL render menu items
2. WHEN a menu item has role restrictions, THEN THE Navigation SHALL hide it if the current user lacks the required role
3. WHEN a menu item is clicked, THEN THE Navigation SHALL navigate using React Router
4. WHEN the current route matches a menu item, THEN THE Navigation SHALL highlight that item as active
5. WHEN a menu item has nested items, THEN THE Navigation SHALL render a submenu
6. WHEN a submenu is collapsed, THEN THE Navigation SHALL expand it when the parent is clicked
7. WHEN a submenu is expanded, THEN THE Navigation SHALL collapse it when the parent is clicked again

### Requirement 16: Layout Components

**User Story:** As a developer, I want layout components, so that I can structure pages consistently.

#### Acceptance Criteria

1. WHEN the PageLayout component receives children, THEN THE PageLayout SHALL render them within a standard page wrapper
2. WHEN the TwoColumnLayout component receives sidebar and main content, THEN THE TwoColumnLayout SHALL render them in a two-column layout
3. WHEN the ThreeColumnLayout component receives left, main, and right content, THEN THE ThreeColumnLayout SHALL render them in a three-column layout
4. WHEN a layout component receives responsive breakpoints, THEN THE layout SHALL adapt to different screen sizes
5. WHEN a layout component receives custom styling props, THEN THE layout SHALL apply those styles

### Requirement 17: Plugin Interface

**User Story:** As a plugin developer, I want a standard interface for creating plugins, so that I can extend EMF UIs consistently.

#### Acceptance Criteria

1. WHEN a Plugin is initialized, THEN THE Plugin SHALL receive an EMF_Client instance
2. WHEN a Plugin is initialized, THEN THE Plugin SHALL receive the current user context
3. WHEN a Plugin is initialized, THEN THE Plugin SHALL receive a router instance
4. WHEN a Plugin's init method is called, THEN THE Plugin SHALL perform initialization logic
5. WHEN a Plugin's mount method is called, THEN THE Plugin SHALL render its UI
6. WHEN a Plugin's unmount method is called, THEN THE Plugin SHALL clean up resources
7. WHEN a Plugin registers custom components, THEN THE Plugin SHALL use the registration API

### Requirement 18: Custom Field Renderers

**User Story:** As a plugin developer, I want to register custom field renderers, so that I can display custom field types.

#### Acceptance Criteria

1. WHEN a plugin registers a Field_Renderer for a field type, THEN THE registration SHALL store the renderer
2. WHEN a component needs to render a field with a registered renderer, THEN THE component SHALL use the custom renderer
3. WHEN a Field_Renderer receives field metadata, THEN THE renderer SHALL have access to field configuration
4. WHEN a Field_Renderer receives a value, THEN THE renderer SHALL display that value appropriately
5. WHEN a Field_Renderer is used in edit mode, THEN THE renderer SHALL provide input controls
6. WHEN a Field_Renderer value changes, THEN THE renderer SHALL call the onChange callback
7. WHEN multiple renderers are registered for the same type, THEN THE last registered renderer SHALL take precedence

### Requirement 19: Custom Page Components

**User Story:** As a plugin developer, I want to register custom page components, so that I can add new page types to EMF UIs.

#### Acceptance Criteria

1. WHEN a plugin registers a page component with a route, THEN THE registration SHALL store the component and route
2. WHEN the router matches a registered route, THEN THE router SHALL render the custom page component
3. WHEN a custom page component is rendered, THEN THE component SHALL receive the EMF_Client instance
4. WHEN a custom page component is rendered, THEN THE component SHALL receive route parameters
5. WHEN a custom page component is rendered, THEN THE component SHALL receive the current user context

### Requirement 20: Package Build and Publishing

**User Story:** As a maintainer, I want to build and publish npm packages, so that developers can install and use the SDK and components.

#### Acceptance Criteria

1. WHEN the build process runs, THEN THE build SHALL compile TypeScript to JavaScript
2. WHEN the build process runs, THEN THE build SHALL generate type declaration files
3. WHEN the build process runs, THEN THE build SHALL bundle dependencies appropriately
4. WHEN the build process runs, THEN THE build SHALL output separate packages for sdk, components, and plugin-sdk
5. WHEN packages are published, THEN THE packages SHALL be available on npm as @emf/sdk, @emf/components, and @emf/plugin-sdk
6. WHEN a package is installed, THEN THE package SHALL include all necessary type definitions
7. WHEN a package is installed, THEN THE package SHALL declare peer dependencies correctly

### Requirement 21: Testing Infrastructure

**User Story:** As a developer, I want comprehensive test coverage, so that I can trust the SDK and components work correctly.

#### Acceptance Criteria

1. WHEN unit tests run, THEN THE tests SHALL validate individual functions and methods
2. WHEN component tests run, THEN THE tests SHALL validate React component behavior
3. WHEN integration tests run, THEN THE tests SHALL validate interactions with mock APIs
4. WHEN tests use property-based testing, THEN THE tests SHALL run at least 100 iterations per property
5. WHEN the test suite runs, THEN THE tests SHALL achieve at least 80% code coverage
6. WHEN tests fail, THEN THE test output SHALL provide clear error messages
7. WHEN tests run in CI, THEN THE tests SHALL complete within a reasonable time limit

### Requirement 22: Documentation

**User Story:** As a developer, I want comprehensive documentation, so that I can learn how to use the SDK and components effectively.

#### Acceptance Criteria

1. WHEN a developer views the documentation, THEN THE documentation SHALL include getting started guides
2. WHEN a developer views the documentation, THEN THE documentation SHALL include API reference for all public methods
3. WHEN a developer views the documentation, THEN THE documentation SHALL include component usage examples
4. WHEN a developer views the documentation, THEN THE documentation SHALL include plugin development guides
5. WHEN a developer views the documentation, THEN THE documentation SHALL include migration guides for breaking changes
6. WHEN a developer views the documentation, THEN THE documentation SHALL include troubleshooting guides
7. WHEN code examples are provided, THEN THE examples SHALL be tested and verified to work
