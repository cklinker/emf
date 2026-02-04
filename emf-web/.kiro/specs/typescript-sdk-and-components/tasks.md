# Implementation Plan: TypeScript SDK and React Components

## Overview

This implementation plan breaks down the TypeScript SDK and React Components feature into discrete, incremental tasks. The plan follows a bottom-up approach: building the core SDK first, then components that depend on it, and finally the plugin SDK. Each task builds on previous work, with testing integrated throughout to catch errors early.

## Tasks

- [x] 1. Set up monorepo structure and build tooling
  - Create packages/sdk, packages/components, packages/plugin-sdk directories
  - Configure Vite for building each package
  - Set up TypeScript configurations with path aliases
  - Configure Vitest for testing
  - Install dependencies: axios, zod, react, react-router, @tanstack/react-query, fast-check
  - Set up ESLint and Prettier
  - _Requirements: 20.1, 20.2, 20.3, 20.4_

- [ ] 2. Implement core EMFClient class
  - [x] 2.1 Create EMFClient with configuration handling
    - Implement constructor accepting EMFClientConfig
    - Store base URL and optional token provider
    - Initialize Axios instance with custom options
    - _Requirements: 1.1, 1.2, 1.3, 1.4_
  
  - [ ]* 2.2 Write property test for base URL propagation
    - **Property 1: Base URL propagation**
    - **Validates: Requirements 1.1**
  
  - [ ]* 2.3 Write property test for token provider integration
    - **Property 2: Token provider integration**
    - **Validates: Requirements 1.2, 1.3, 9.5**
  
  - [ ]* 2.4 Write property test for HTTP client options
    - **Property 3: HTTP client options propagation**
    - **Validates: Requirements 1.4**

- [ ] 3. Implement resource discovery
  - [x] 3.1 Create discovery method with caching
    - Implement discover() method to fetch from /api/_meta/resources
    - Add in-memory cache with TTL support
    - Parse and validate response with Zod schema
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [ ]* 3.2 Write property test for discovery response parsing
    - **Property 4: Discovery response parsing**
    - **Validates: Requirements 2.2**
  
  - [ ]* 3.3 Write property test for cache behavior
    - **Property 5: Discovery cache behavior**
    - **Validates: Requirements 2.3**
  
  - [ ]* 3.4 Write unit tests for cache expiration
    - Test TTL expiration triggers new fetch
    - _Requirements: 2.4_

- [ ] 4. Implement error handling system
  - [x] 4.1 Create error class hierarchy
    - Implement EMFError base class
    - Implement ValidationError, AuthenticationError, AuthorizationError, NotFoundError, ServerError, NetworkError
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_
  
  - [x] 4.2 Create error response parser
    - Parse API error responses and map to error classes
    - Extract field-level errors for ValidationError
    - _Requirements: 2.5, 4.7_
  
  - [ ]* 4.3 Write property test for error response mapping
    - **Property 6: Error response mapping**
    - **Validates: Requirements 2.5, 4.7, 8.1-8.6**
  
  - [ ]* 4.4 Write unit tests for specific error types
    - Test 400 → ValidationError with field errors
    - Test 401 → AuthenticationError
    - Test 403 → AuthorizationError
    - Test 404 → NotFoundError
    - Test 500 → ServerError
    - Test network failure → NetworkError
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 5. Implement retry logic with exponential backoff
  - [x] 5.1 Create retry interceptor for Axios
    - Implement exponential backoff calculation
    - Configure retryable error detection (network errors, 5xx, 429)
    - Add max attempts and max delay limits
    - _Requirements: 8.7_
  
  - [ ]* 5.2 Write property test for retry behavior
    - **Property 20: Retry with exponential backoff**
    - **Validates: Requirements 8.7**
  
  - [ ]* 5.3 Write unit tests for retry scenarios
    - Test retry on network error
    - Test retry on 500/502/503/504
    - Test no retry on 4xx (except 429)
    - Test max attempts limit
    - _Requirements: 8.7_

- [ ] 6. Implement authentication and token management
  - [x] 6.1 Create TokenManager class
    - Implement token caching and expiration checking
    - Implement automatic token refresh
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  
  - [x] 6.2 Create Axios interceptor for token injection
    - Call TokenProvider before each request
    - Inject token into Authorization header
    - _Requirements: 9.1, 9.2_
  
  - [ ]* 6.3 Write property test for token provider invocation
    - **Property 21: Token provider invocation**
    - **Validates: Requirements 9.1**
  
  - [ ]* 6.4 Write property test for token header injection
    - **Property 22: Token header injection**
    - **Validates: Requirements 9.2**
  
  - [ ]* 6.5 Write property test for token refresh
    - **Property 23: Token refresh on expiration**
    - **Validates: Requirements 9.3**
  
  - [ ]* 6.6 Write unit test for refresh failure
    - Test AuthenticationError thrown on refresh failure
    - _Requirements: 9.4_

- [ ] 7. Implement response validation with Zod
  - [x] 7.1 Create Zod schemas for API responses
    - Define ResourceMetadataSchema
    - Define ListResponseSchema
    - Define ErrorResponseSchema
    - _Requirements: 10.1_
  
  - [x] 7.2 Create validation interceptor
    - Validate responses against expected schemas
    - Throw ValidationError on mismatch
    - Support disabling validation via config
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  
  - [ ]* 7.3 Write property test for response validation
    - **Property 9: Response validation**
    - **Validates: Requirements 3.6, 10.1, 10.3**
  
  - [ ]* 7.4 Write property test for validation bypass
    - **Property 24: Validation bypass**
    - **Validates: Requirements 10.4**

- [ ] 8. Implement ResourceClient for CRUD operations
  - [x] 8.1 Create ResourceClient class
    - Implement list() method with ListOptions support
    - Implement get(id) method
    - Implement create(data) method
    - Implement update(id, data) method
    - Implement patch(id, data) method
    - Implement delete(id) method
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_
  
  - [ ]* 8.2 Write property test for resource URL construction
    - **Property 7: Resource URL construction**
    - **Validates: Requirements 3.1, 4.1-4.5**
  
  - [ ]* 8.3 Write property test for query parameter construction
    - **Property 8: Query parameter construction**
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.5**
  
  - [ ]* 8.4 Write property test for list response structure
    - **Property 10: List response structure**
    - **Validates: Requirements 3.7**
  
  - [ ]* 8.5 Write property test for CRUD HTTP methods
    - **Property 11: CRUD operation HTTP methods**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**
  
  - [ ]* 8.6 Write property test for CRUD payloads
    - **Property 12: CRUD operation payloads**
    - **Validates: Requirements 4.2, 4.3, 4.4**
  
  - [ ]* 8.7 Write property test for successful operation response
    - **Property 13: Successful operation response**
    - **Validates: Requirements 4.6**

- [ ] 9. Implement QueryBuilder for fluent query construction
  - [x] 9.1 Create QueryBuilder class
    - Implement paginate(page, size) method
    - Implement sort(field, direction) method
    - Implement filter(field, operator, value) method
    - Implement fields(...fieldNames) method
    - Implement execute() method
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_
  
  - [ ]* 9.2 Write property test for state accumulation
    - **Property 14: Query builder state accumulation**
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**
  
  - [ ]* 9.3 Write property test for multiple filter combination
    - **Property 15: Multiple filter combination**
    - **Validates: Requirements 5.6**
  
  - [ ]* 9.4 Write property test for sort order preservation
    - **Property 16: Sort order preservation**
    - **Validates: Requirements 5.7**

- [x] 10. Checkpoint - Ensure core SDK tests pass
  - Run all SDK unit and property tests
  - Verify error handling works correctly
  - Ensure all tests pass, ask the user if questions arise

- [ ] 11. Implement AdminClient for control plane operations
  - [x] 11.1 Create AdminClient class with nested operation groups
    - Implement collections operations (list, get, create, update, delete)
    - Implement fields operations (add, update, delete)
    - Implement authz operations (roles and policies CRUD)
    - Implement oidc operations (provider CRUD)
    - Implement ui operations (config get/update)
    - Implement packages operations (export, import)
    - Implement migrations operations (list, run, rollback)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  
  - [ ]* 11.2 Write property test for admin endpoint construction
    - **Property 17: Admin endpoint construction**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**
  
  - [ ]* 11.3 Write unit tests for admin operations
    - Test collection management endpoints
    - Test field management endpoints
    - Test authz management endpoints
    - _Requirements: 6.1, 6.2, 6.3_

- [ ] 12. Implement type generation CLI
  - [x] 12.1 Create CLI tool for OpenAPI type generation
    - Fetch OpenAPI spec from URL
    - Parse collection schemas from spec
    - Generate TypeScript interfaces for collections
    - Generate request/response types
    - Write types to output file
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [ ]* 12.2 Write property test for type generation
    - **Property 18: Type generation from OpenAPI**
    - **Validates: Requirements 7.2, 7.3, 7.4**
  
  - [ ]* 12.3 Write property test for invalid OpenAPI handling
    - **Property 19: Invalid OpenAPI error reporting**
    - **Validates: Requirements 7.6**
  
  - [ ]* 12.4 Write unit tests for CLI
    - Test fetching OpenAPI spec
    - Test file writing
    - _Requirements: 7.1, 7.5_

- [ ] 13. Build and test @emf/sdk package
  - [x] 13.1 Configure package.json for @emf/sdk
    - Set package name, version, exports
    - Declare peer dependencies
    - Configure build scripts
    - _Requirements: 20.5, 20.6, 20.7_
  
  - [x] 13.2 Build SDK package
    - Run TypeScript compilation
    - Generate type declarations
    - Bundle with Vite
    - _Requirements: 20.1, 20.2, 20.3_
  
  - [ ]* 13.3 Run integration tests for SDK
    - Test complete CRUD workflows with mock server
    - Test discovery → list → create → update → delete flow
    - Test error handling across operations
    - _Requirements: 21.3_

- [x] 14. Checkpoint - Verify SDK package is complete
  - Verify all SDK tests pass
  - Verify package builds successfully
  - Verify type declarations are generated
  - Ensure all tests pass, ask the user if questions arise

- [ ] 15. Implement DataTable component
  - [x] 15.1 Create DataTable component with TanStack Query integration
    - Implement data fetching using EMFClient
    - Implement column rendering based on configuration
    - Implement sorting on column header click
    - Implement pagination controls
    - Implement filter application
    - Add loading and error states
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_
  
  - [ ]* 15.2 Write property test for column rendering order
    - **Property 25: DataTable column rendering order**
    - **Validates: Requirements 11.2**
  
  - [ ]* 15.3 Write unit tests for DataTable
    - Test data fetching on mount
    - Test sort on column click
    - Test pagination controls
    - Test filter application
    - Test loading state display
    - Test error state display
    - _Requirements: 11.1, 11.3, 11.4, 11.5, 11.6, 11.7_

- [ ] 16. Implement ResourceForm component
  - [x] 16.1 Create ResourceForm with React Hook Form
    - Fetch collection schema on mount
    - Generate form fields from schema
    - Fetch existing data for edit mode (when recordId provided)
    - Implement form validation with Zod
    - Implement create/update operation selection
    - Call onSave/onCancel callbacks
    - Handle field-level authorization
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8_
  
  - [ ]* 16.2 Write property test for field generation
    - **Property 26: ResourceForm field generation**
    - **Validates: Requirements 12.2**
  
  - [ ]* 16.3 Write property test for operation selection
    - **Property 27: ResourceForm operation selection**
    - **Validates: Requirements 12.5**
  
  - [ ]* 16.4 Write property test for field authorization
    - **Property 28: ResourceForm field authorization**
    - **Validates: Requirements 12.8**
  
  - [ ]* 16.5 Write unit tests for ResourceForm
    - Test schema fetching
    - Test data fetching for edit mode
    - Test form validation
    - Test onSave callback
    - Test onCancel callback
    - _Requirements: 12.1, 12.3, 12.4, 12.6, 12.7_

- [ ] 17. Implement ResourceDetail component
  - [x] 17.1 Create ResourceDetail component
    - Fetch resource data and schema on mount
    - Render fields based on schema
    - Support custom field renderers from registry
    - Use default renderers for standard types
    - Handle field-level authorization
    - Add loading and error states
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_
  
  - [ ]* 17.2 Write property test for field rendering
    - **Property 29: ResourceDetail field rendering**
    - **Validates: Requirements 13.2**
  
  - [ ]* 17.3 Write property test for field authorization
    - **Property 30: ResourceDetail field authorization**
    - **Validates: Requirements 13.5**
  
  - [ ]* 17.4 Write unit tests for ResourceDetail
    - Test data fetching
    - Test custom renderer usage
    - Test default renderer fallback
    - Test loading state
    - Test error state
    - _Requirements: 13.1, 13.3, 13.4, 13.6, 13.7_

- [ ] 18. Implement FilterBuilder component
  - [x] 18.1 Create FilterBuilder component
    - Display available fields from schema
    - Implement add/remove filter UI
    - Show appropriate operators for field types
    - Provide type-appropriate value inputs
    - Call onFilterChange callback on changes
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7_
  
  - [ ]* 18.2 Write property test for field display
    - **Property 31: FilterBuilder field display**
    - **Validates: Requirements 14.1**
  
  - [ ]* 18.3 Write property test for operator filtering
    - **Property 32: FilterBuilder operator filtering**
    - **Validates: Requirements 14.3, 14.6, 14.7**
  
  - [ ]* 18.4 Write property test for change callback
    - **Property 33: FilterBuilder change callback**
    - **Validates: Requirements 14.5**
  
  - [ ]* 18.5 Write unit tests for FilterBuilder
    - Test add filter interaction
    - Test remove filter interaction
    - Test string field operators
    - Test number field operators
    - _Requirements: 14.2, 14.4, 14.6, 14.7_

- [ ] 19. Implement Navigation component
  - [x] 19.1 Create Navigation component with React Router integration
    - Render menu items from configuration
    - Filter items based on user roles
    - Handle navigation on click
    - Highlight active menu item
    - Support nested menus with expand/collapse
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7_
  
  - [ ]* 19.2 Write property test for menu rendering
    - **Property 34: Navigation menu rendering**
    - **Validates: Requirements 15.1**
  
  - [ ]* 19.3 Write property test for role-based filtering
    - **Property 35: Navigation role-based filtering**
    - **Validates: Requirements 15.2**
  
  - [ ]* 19.4 Write property test for active state
    - **Property 36: Navigation active state**
    - **Validates: Requirements 15.4**
  
  - [ ]* 19.5 Write property test for nested menu rendering
    - **Property 37: Navigation nested menu rendering**
    - **Validates: Requirements 15.5**
  
  - [ ]* 19.6 Write unit tests for Navigation
    - Test navigation on click
    - Test submenu expand
    - Test submenu collapse
    - _Requirements: 15.3, 15.6, 15.7_

- [ ] 20. Implement Layout components
  - [x] 20.1 Create PageLayout, TwoColumnLayout, ThreeColumnLayout
    - Implement PageLayout with header/footer support
    - Implement TwoColumnLayout with configurable sidebar
    - Implement ThreeColumnLayout with left/right sidebars
    - Add responsive behavior with breakpoints
    - Support custom styling props
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5_
  
  - [ ]* 20.2 Write property test for responsive adaptation
    - **Property 38: Layout responsive adaptation**
    - **Validates: Requirements 16.4**
  
  - [ ]* 20.3 Write property test for style application
    - **Property 39: Layout style application**
    - **Validates: Requirements 16.5**
  
  - [ ]* 20.4 Write unit tests for layouts
    - Test PageLayout rendering
    - Test TwoColumnLayout rendering
    - Test ThreeColumnLayout rendering
    - _Requirements: 16.1, 16.2, 16.3_

- [ ] 21. Build and test @emf/components package
  - [x] 21.1 Configure package.json for @emf/components
    - Set package name, version, exports
    - Declare peer dependencies (React, @emf/sdk)
    - Configure build scripts
    - _Requirements: 20.5, 20.6, 20.7_
  
  - [x] 21.2 Build components package
    - Run TypeScript compilation
    - Generate type declarations
    - Bundle with Vite
    - _Requirements: 20.1, 20.2, 20.3_
  
  - [ ]* 21.3 Run integration tests for components
    - Test DataTable with mock API
    - Test ResourceForm submission flow
    - Test component interactions
    - _Requirements: 21.3_

- [x] 22. Checkpoint - Verify components package is complete
  - Verify all component tests pass
  - Verify package builds successfully
  - Verify components work with SDK
  - Ensure all tests pass, ask the user if questions arise

- [ ] 23. Implement Plugin interface and base class
  - [x] 23.1 Create Plugin interface and BasePlugin class
    - Define Plugin interface with init, mount, unmount methods
    - Implement BasePlugin abstract class
    - Define PluginContext interface
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7_
  
  - [ ]* 23.2 Write unit tests for plugin lifecycle
    - Test plugin initialization with context
    - Test init method call
    - Test mount method call
    - Test unmount method call
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6_

- [ ] 24. Implement ComponentRegistry
  - [x] 24.1 Create ComponentRegistry for field renderers and page components
    - Implement field renderer registration and retrieval
    - Implement page component registration and retrieval
    - Support last-wins precedence for duplicate registrations
    - _Requirements: 18.1, 18.2, 18.7, 19.1_
  
  - [ ]* 24.2 Write property test for field renderer registration
    - **Property 40: Field renderer registration and retrieval**
    - **Validates: Requirements 18.1**
  
  - [ ]* 24.3 Write property test for field renderer value display
    - **Property 41: Field renderer value display**
    - **Validates: Requirements 18.4**
  
  - [ ]* 24.4 Write property test for last-wins precedence
    - **Property 42: Field renderer last-wins precedence**
    - **Validates: Requirements 18.7**
  
  - [ ]* 24.5 Write property test for page component registration
    - **Property 43: Page component registration and retrieval**
    - **Validates: Requirements 19.1**
  
  - [ ]* 24.6 Write unit tests for ComponentRegistry
    - Test custom renderer usage in components
    - Test field metadata passing
    - Test edit mode input controls
    - Test onChange callback
    - Test page component rendering
    - Test page component props
    - _Requirements: 18.2, 18.3, 18.5, 18.6, 19.2, 19.3, 19.4, 19.5_

- [ ] 25. Build and test @emf/plugin-sdk package
  - [x] 25.1 Configure package.json for @emf/plugin-sdk
    - Set package name, version, exports
    - Declare peer dependencies (@emf/sdk)
    - Configure build scripts
    - _Requirements: 20.5, 20.6, 20.7_
  
  - [x] 25.2 Build plugin-sdk package
    - Run TypeScript compilation
    - Generate type declarations
    - Bundle with Vite
    - _Requirements: 20.1, 20.2, 20.3_
  
  - [ ]* 25.3 Write integration test for plugin registration
    - Test complete plugin lifecycle
    - Test custom renderer registration and usage
    - Test custom page component registration
    - _Requirements: 17.7, 21.3_

- [ ] 26. Create example applications
  - [x] 26.1 Create basic SDK usage example
    - Demonstrate EMFClient initialization
    - Demonstrate CRUD operations
    - Demonstrate query building
    - _Requirements: 22.1, 22.2_
  
  - [x] 26.2 Create component usage examples
    - Demonstrate DataTable usage
    - Demonstrate ResourceForm usage
    - Demonstrate Navigation and layouts
    - _Requirements: 22.3_
  
  - [x] 26.3 Create plugin development example
    - Demonstrate custom field renderer
    - Demonstrate custom page component
    - _Requirements: 22.4_

- [ ] 27. Write documentation
  - [x] 27.1 Create getting started guide
    - Installation instructions
    - Basic usage examples
    - Configuration options
    - _Requirements: 22.1_
  
  - [x] 27.2 Create API reference documentation
    - Document all public classes and methods
    - Document component props and usage
    - Document plugin interfaces
    - _Requirements: 22.2, 22.3, 22.4_
  
  - [x] 27.3 Create troubleshooting guide
    - Common errors and solutions
    - Debugging tips
    - FAQ
    - _Requirements: 22.6_

- [x] 28. Final checkpoint - Complete package verification
  - Run all tests across all packages
  - Verify all packages build successfully
  - Verify examples work correctly
  - Verify documentation is complete
  - Check code coverage meets 80% threshold
  - Ensure all tests pass, ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (minimum 100 iterations each)
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end workflows
- The implementation follows a bottom-up approach: SDK → Components → Plugin SDK
- All packages are built with Vite and tested with Vitest
- Property-based testing uses fast-check library
- Component testing uses React Testing Library
- API mocking uses MSW (Mock Service Worker)
