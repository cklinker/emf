# Implementation Plan: EMF Admin/Builder UI

## Overview

This implementation plan breaks down the EMF Admin/Builder UI into discrete coding tasks. The application is built with React 18+, TypeScript, React Router 6+, and TanStack Query, integrating with @emf/sdk, @emf/components, and @emf/plugin-sdk packages.

## Tasks

- [x] 1. Project Setup and Core Infrastructure
  - [x] 1.1 Initialize Vite React TypeScript project
    - Create project with `npm create vite@latest app -- --template react-ts`
    - Configure TypeScript with strict mode
    - Set up path aliases in tsconfig.json
    - _Requirements: Technical setup_

  - [x] 1.2 Install and configure dependencies
    - Install React Router 6, TanStack Query, React Hook Form, Zod
    - Install @emf/sdk, @emf/components, @emf/plugin-sdk
    - Install Vitest, React Testing Library, fast-check, MSW
    - Configure Vitest with React Testing Library
    - _Requirements: Technical setup_

  - [x] 1.3 Set up project structure
    - Create directory structure: components/, pages/, hooks/, context/, services/, utils/
    - Create index files for each directory
    - Set up CSS modules configuration
    - _Requirements: Technical setup_

  - [x] 1.4 Configure ESLint and Prettier
    - Install ESLint with React and TypeScript plugins
    - Configure accessibility linting rules (eslint-plugin-jsx-a11y)
    - Set up Prettier with consistent formatting
    - _Requirements: Technical setup_

- [x] 2. Context Providers and Core Hooks
  - [x] 2.1 Implement AuthContext and useAuth hook
    - Create AuthContext with user state, login, logout, getAccessToken
    - Implement OIDC authentication flow
    - Handle token storage and refresh
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

  - [ ]* 2.2 Write property tests for authentication
    - **Property 5: API requests include authentication**
    - **Property 6: 401 responses trigger token refresh**
    - **Validates: Requirements 2.7, 2.8**

  - [x] 2.3 Implement ConfigContext and useConfig hook
    - Create ConfigContext with bootstrap config state
    - Fetch bootstrap config from /ui/config/bootstrap
    - Handle loading and error states
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_

  - [ ]* 2.4 Write property tests for configuration
    - **Property 1: Bootstrap configuration applies routes**
    - **Property 2: Bootstrap configuration applies menus**
    - **Property 3: Bootstrap configuration applies theme**
    - **Property 4: Bootstrap configuration applies branding**
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5**

  - [x] 2.5 Implement ThemeContext and useTheme hook
    - Create ThemeContext with mode state (light/dark/system)
    - Apply CSS custom properties based on theme
    - Persist theme preference to localStorage
    - Detect system theme preference
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7_

  - [ ]* 2.6 Write property tests for theming
    - **Property 78: Light and dark themes are available**
    - **Property 79: Theme preference persists**
    - **Property 80: Theme changes without reload**
    - **Property 81: Bootstrap theme is applied**
    - **Property 82: Both themes meet contrast requirements**
    - **Validates: Requirements 16.1, 16.4, 16.5, 16.6, 16.7**

  - [x] 2.7 Implement I18nContext and useI18n hook
    - Create I18nContext with locale state and translation function
    - Load translations from JSON files
    - Implement date, number, and currency formatting
    - Handle RTL text direction
    - Persist language preference
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7_

  - [ ]* 2.8 Write property tests for internationalization
    - **Property 73: Multiple languages are supported**
    - **Property 74: Language preference persists**
    - **Property 75: Language changes without reload**
    - **Property 76: RTL languages are supported**
    - **Property 77: Locale formatting is applied**
    - **Validates: Requirements 15.1, 15.4, 15.5, 15.6, 15.7**

- [x] 3. Checkpoint - Core infrastructure complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Application Shell Components
  - [x] 4.1 Implement AppShell component
    - Create main layout wrapper with header, sidebar, and content areas
    - Handle responsive layout changes
    - Integrate with ThemeContext for styling
    - _Requirements: 17.1, 17.2, 17.3_

  - [x] 4.2 Implement Header component
    - Display branding (logo, app name) from config
    - Display user menu with logout option
    - Handle responsive behavior
    - _Requirements: 1.5, 2.6_

  - [x] 4.3 Implement Sidebar component
    - Render navigation menus from config
    - Support nested menu items
    - Handle collapsed state on mobile
    - _Requirements: 1.3, 17.4_

  - [ ]* 4.4 Write property tests for responsive layout
    - **Property 83: Desktop layout is applied**
    - **Property 84: Tablet layout is applied**
    - **Property 85: Mobile layout is applied**
    - **Property 86: Mobile navigation collapses**
    - **Property 87: Mobile forms stack vertically**
    - **Validates: Requirements 17.1, 17.2, 17.3, 17.4, 17.5**

  - [x] 4.5 Implement ErrorBoundary component
    - Create error boundary with fallback UI
    - Log errors to console with stack trace
    - Provide recovery options (reload, navigate home)
    - _Requirements: 18.7, 18.8_

  - [ ]* 4.6 Write property tests for error handling
    - **Property 93: Error boundary catches errors**
    - **Validates: Requirements 18.7**

- [x] 5. Shared UI Components
  - [x] 5.1 Implement Toast notification system
    - Create Toast component with success, error, warning, info variants
    - Create ToastProvider and useToast hook
    - Handle auto-dismiss and manual close
    - _Requirements: 18.1, 18.3_

  - [x] 5.2 Implement ConfirmDialog component
    - Create modal dialog with confirm/cancel actions
    - Support danger variant for destructive actions
    - Handle keyboard interactions (Escape to close)
    - _Requirements: 3.10, 4.8, 5.5, 6.7_

  - [x] 5.3 Implement LoadingSpinner component
    - Create spinner with size variants
    - Support accessible loading label
    - _Requirements: 18.4_

  - [x] 5.4 Implement ErrorMessage component
    - Display error message with optional retry button
    - Support different error types
    - _Requirements: 18.1, 18.5_

  - [ ]* 5.5 Write property tests for feedback components
    - **Property 88: API errors display messages**
    - **Property 89: Success messages are displayed**
    - **Property 90: Loading indicators are shown**
    - **Property 91: Network errors offer retry**
    - **Property 92: Errors are logged with detail**
    - **Validates: Requirements 18.1, 18.3, 18.4, 18.5, 18.6**

- [x] 6. Checkpoint - Shell and shared components complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Collection Management Pages
  - [x] 7.1 Implement CollectionsPage
    - Display paginated list using DataTable from @emf/components
    - Implement filtering by name and status
    - Implement sorting by name, created date, modified date
    - Add create, edit, delete actions
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.10, 3.11_

  - [ ]* 7.2 Write property tests for collection list
    - **Property 7: Collection list displays all collections**
    - **Property 8: Collection filtering returns matching results**
    - **Property 9: Collection sorting orders results correctly**
    - **Validates: Requirements 3.1, 3.2, 3.3**

  - [x] 7.3 Implement CollectionDetailPage
    - Display collection metadata
    - Display fields list with FieldsPanel
    - Display authorization configuration
    - Display version history
    - _Requirements: 3.7, 3.8, 3.12_

  - [ ]* 7.4 Write property tests for collection detail
    - **Property 10: Collection detail displays metadata and fields**
    - **Property 11: Collection version history displays all versions**
    - **Validates: Requirements 3.8, 3.12**

  - [x] 7.5 Implement CollectionForm component
    - Create form for collection creation and editing
    - Validate required fields
    - Handle form submission with API call
    - Display validation errors inline
    - _Requirements: 3.4, 3.5, 3.6, 3.9_

  - [ ]* 7.6 Write property tests for collection forms
    - **Property 16: Form submission calls API with valid data** (collection)
    - **Property 17: Form validation errors display inline** (collection)
    - **Property 18: Edit forms pre-populate with current values** (collection)
    - **Validates: Requirements 3.5, 3.6, 3.9**

- [x] 8. Field Management Components
  - [x] 8.1 Implement FieldsPanel component
    - Display sortable list of active fields
    - Support drag-and-drop reordering
    - Add, edit, delete field actions
    - _Requirements: 4.1, 4.8, 4.9, 4.10_

  - [ ]* 8.2 Write property tests for field list
    - **Property 12: Field list displays active fields**
    - **Property 14: Field reordering updates order**
    - **Validates: Requirements 4.1, 4.10**

  - [x] 8.3 Implement FieldEditor component
    - Create form for all field types
    - Handle reference field with collection dropdown
    - Configure validation rules (required, min, max, pattern, email, url)
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.11_

  - [ ]* 8.4 Write property tests for field editor
    - **Property 13: All field types are supported**
    - **Property 15: Field validation rules are configurable**
    - **Property 16: Form submission calls API with valid data** (field)
    - **Property 17: Form validation errors display inline** (field)
    - **Property 18: Edit forms pre-populate with current values** (field)
    - **Validates: Requirements 4.3, 4.5, 4.6, 4.7, 4.11**

- [x] 9. Checkpoint - Collection management complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Authorization Management Pages
  - [x] 10.1 Implement RolesPage
    - Display list of all roles
    - Add create, edit, delete actions
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 10.2 Write property tests for roles
    - **Property 19: Roles list displays all roles**
    - **Property 16: Form submission calls API with valid data** (role)
    - **Property 18: Edit forms pre-populate with current values** (role)
    - **Validates: Requirements 5.1, 5.3, 5.4**

  - [x] 10.3 Implement PoliciesPage
    - Display list of all policies
    - Add create, edit, delete actions
    - _Requirements: 5.6, 5.7, 5.8_

  - [ ]* 10.4 Write property tests for policies
    - **Property 20: Policies list displays all policies**
    - **Property 16: Form submission calls API with valid data** (policy)
    - **Validates: Requirements 5.6, 5.8**

  - [x] 10.5 Implement AuthorizationPanel component
    - Display route-level authorization configuration
    - Display field-level authorization configuration
    - Allow selecting policies for each operation
    - _Requirements: 5.9, 5.10, 5.11, 5.12_

  - [ ]* 10.6 Write property tests for authorization panel
    - **Property 21: Route authorization is configurable per operation**
    - **Property 22: Field authorization is configurable per field**
    - **Validates: Requirements 5.9, 5.10, 5.11, 5.12**

- [x] 11. OIDC Provider Management Page
  - [x] 11.1 Implement OIDCProvidersPage
    - Display list of all providers with status
    - Add, edit, delete provider actions
    - Test connection functionality
    - _Requirements: 6.1, 6.2, 6.7, 6.8, 6.9_

  - [ ]* 11.2 Write property tests for OIDC providers
    - **Property 23: OIDC providers list displays all providers**
    - **Validates: Requirements 6.1, 6.9**

  - [x] 11.3 Implement OIDCProviderForm component
    - Create form with required fields (issuer, client ID, scopes)
    - Validate form inputs
    - Handle form submission
    - _Requirements: 6.3, 6.4, 6.5, 6.6_

  - [ ]* 11.4 Write property tests for OIDC provider form
    - **Property 24: OIDC provider form requires essential fields**
    - **Property 16: Form submission calls API with valid data** (OIDC provider)
    - **Property 17: Form validation errors display inline** (OIDC provider)
    - **Property 18: Edit forms pre-populate with current values** (OIDC provider)
    - **Validates: Requirements 6.3, 6.4, 6.5, 6.6**

- [x] 12. Checkpoint - Admin pages complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. UI Builder - Page Management
  - [x] 13.1 Implement PageBuilderPage
    - Display list of all pages
    - Create page editor with canvas
    - Implement component palette
    - Implement property panel
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

  - [ ]* 13.2 Write property tests for page builder
    - **Property 25: Pages list displays all pages**
    - **Property 26: Page configuration supports required fields**
    - **Property 27: Component addition from palette**
    - **Property 28: Component properties are editable**
    - **Validates: Requirements 7.1, 7.3, 7.4, 7.5**

  - [x] 13.3 Implement page preview and save functionality
    - Preview mode for page
    - Save page configuration via API
    - Publish page functionality
    - Duplicate page functionality
    - _Requirements: 7.7, 7.8, 7.9, 7.10_

  - [ ]* 13.4 Write property tests for page operations
    - **Property 29: Page save persists configuration**
    - **Property 30: Page duplication creates copy**
    - **Validates: Requirements 7.8, 7.10**

- [x] 14. UI Builder - Menu Management
  - [x] 14.1 Implement MenuBuilderPage
    - Display list of all menus
    - Create menu editor with tree view
    - Support drag-and-drop reordering
    - Support nested menu items
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ]* 14.2 Write property tests for menu builder
    - **Property 31: Menus list displays all menus**
    - **Property 32: Menu items support CRUD operations**
    - **Property 33: Menu item reordering updates order**
    - **Property 34: Nested menu items are supported**
    - **Validates: Requirements 8.1, 8.3, 8.4, 8.5**

  - [x] 14.3 Implement menu item configuration and preview
    - Configure label, path, icon, access policies
    - Save menu configuration via API
    - Display menu preview
    - _Requirements: 8.6, 8.7, 8.8_

  - [ ]* 14.4 Write property tests for menu operations
    - **Property 35: Menu item configuration supports all fields**
    - **Property 36: Menu save persists configuration**
    - **Property 37: Menu preview reflects current state**
    - **Validates: Requirements 8.6, 8.7, 8.8**

- [x] 15. Checkpoint - UI Builder complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Package Management Page
  - [x] 16.1 Implement PackagesPage
    - Display export and import options
    - Display package history
    - _Requirements: 9.1, 9.10_

  - [x] 16.2 Implement export functionality
    - Create export form with item selection
    - Support selecting collections, authz, pages, menus
    - Generate and download package file
    - _Requirements: 9.2, 9.3, 9.4_

  - [ ]* 16.3 Write property tests for export
    - **Property 38: Export supports all item types**
    - **Validates: Requirements 9.3**

  - [x] 16.4 Implement import functionality
    - Create file upload interface
    - Display import preview
    - Support dry-run mode
    - Apply import and display results
    - Handle import errors
    - _Requirements: 9.5, 9.6, 9.7, 9.8, 9.9_

  - [ ]* 16.5 Write property tests for import
    - **Property 39: Import preview shows changes**
    - **Property 40: Package history displays all operations**
    - **Validates: Requirements 9.6, 9.10**

- [x] 17. Migration Management Page
  - [x] 17.1 Implement MigrationsPage
    - Display migration history
    - Display migration run details
    - _Requirements: 10.1, 10.8_

  - [ ]* 17.2 Write property tests for migration history
    - **Property 41: Migration history displays all runs**
    - **Property 44: Migration history shows step details**
    - **Validates: Requirements 10.1, 10.8**

  - [x] 17.3 Implement migration planning
    - Create plan form with schema selection
    - Display migration steps
    - Display estimated impact and risks
    - _Requirements: 10.2, 10.3, 10.4_

  - [ ]* 17.4 Write property tests for migration planning
    - **Property 42: Migration plan displays steps**
    - **Property 43: Migration plan displays risks**
    - **Validates: Requirements 10.3, 10.4**

  - [x] 17.5 Implement migration execution
    - Execute migration with progress tracking
    - Display real-time progress updates
    - Handle errors and offer rollback
    - _Requirements: 10.5, 10.6, 10.7_

- [x] 18. Checkpoint - Package and migration management complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 19. Resource Browser
  - [x] 19.1 Implement ResourceBrowserPage
    - Display list of available collections
    - Navigate to collection data view
    - _Requirements: 11.1_

  - [ ]* 19.2 Write property tests for resource browser
    - **Property 45: Resource browser lists collections**
    - **Validates: Requirements 11.1**

  - [x] 19.3 Implement ResourceListPage
    - Display paginated data table using DataTable
    - Integrate FilterBuilder for filtering
    - Support column sorting and selection
    - Support bulk selection
    - _Requirements: 11.2, 11.3, 11.4, 11.5, 11.11_

  - [ ]* 19.4 Write property tests for resource list
    - **Property 46: Record list displays paginated data**
    - **Property 47: Record filtering returns matching results**
    - **Property 48: Record sorting orders results correctly**
    - **Property 49: Column selection controls visibility**
    - **Property 52: Bulk selection enables bulk operations**
    - **Validates: Requirements 11.2, 11.3, 11.4, 11.5, 11.11**

  - [x] 19.5 Implement ResourceDetailPage
    - Display all field values with formatting
    - Edit and delete actions
    - _Requirements: 11.7, 11.8, 11.10_

  - [ ]* 19.6 Write property tests for resource detail
    - **Property 51: Record detail displays all fields**
    - **Validates: Requirements 11.8**

  - [x] 19.7 Implement ResourceFormPage
    - Generate form from collection schema
    - Handle create and edit modes
    - _Requirements: 11.6, 11.9_

  - [ ]* 19.8 Write property tests for resource form
    - **Property 50: Record form generated from schema**
    - **Property 18: Edit forms pre-populate with current values** (record)
    - **Validates: Requirements 11.6, 11.9**

  - [x] 19.9 Implement export functionality
    - Export selected records to CSV
    - Export selected records to JSON
    - _Requirements: 11.12_

  - [ ]* 19.10 Write property tests for export
    - **Property 53: Export generates valid format**
    - **Validates: Requirements 11.12**

- [x] 20. Checkpoint - Resource browser complete
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 21. Plugin System Integration
  - [x] 21.1 Implement PluginProvider and usePlugins hook
    - Load and initialize configured plugins
    - Manage ComponentRegistry for field renderers and page components
    - Handle plugin lifecycle hooks
    - Handle plugin load failures gracefully
    - _Requirements: 12.1, 12.2, 12.3, 12.7, 12.8_

  - [ ]* 21.2 Write property tests for plugin loading
    - **Property 54: Plugins load on startup**
    - **Property 55: Custom field renderers are registered**
    - **Property 56: Custom page components are registered**
    - **Property 59: Plugin failures don't block loading**
    - **Property 60: Plugin lifecycle hooks are called**
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.7, 12.8**

  - [x] 21.3 Integrate custom renderers in forms
    - Use registered field renderers when rendering fields
    - Fall back to default renderers for unregistered types
    - _Requirements: 12.4_

  - [ ]* 21.4 Write property tests for custom renderers
    - **Property 57: Custom field renderers are used**
    - **Validates: Requirements 12.4**

  - [x] 21.5 Integrate custom components in pages
    - Use registered page components when rendering pages
    - _Requirements: 12.5_

  - [ ]* 21.6 Write property tests for custom components
    - **Property 58: Custom page components are used**
    - **Validates: Requirements 12.5**

  - [x] 21.7 Implement plugin configuration UI
    - Display plugin settings interface
    - _Requirements: 12.6_

- [x] 22. Dashboard Page
  - [x] 22.1 Implement DashboardPage
    - Display system health status cards
    - Display metrics charts
    - Display recent errors list
    - _Requirements: 13.1, 13.2, 13.3, 13.4_

  - [ ]* 22.2 Write property tests for dashboard
    - **Property 61: Health status displays all services**
    - **Property 62: Metrics display key indicators**
    - **Property 63: Recent errors are displayed**
    - **Validates: Requirements 13.2, 13.3, 13.4**

  - [x] 22.3 Implement time range and auto-refresh
    - Configure time range for metrics
    - Auto-refresh at configurable interval
    - Display health alerts
    - _Requirements: 13.5, 13.6, 13.7_

  - [ ]* 22.4 Write property tests for dashboard controls
    - **Property 64: Time range controls metrics display**
    - **Property 65: Auto-refresh updates metrics**
    - **Validates: Requirements 13.5, 13.6**

- [x] 23. Checkpoint - Plugin system and dashboard complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 24. Accessibility Implementation
  - [x] 24.1 Implement keyboard navigation
    - Ensure all interactive elements are focusable
    - Implement keyboard shortcuts where appropriate
    - Add skip links for main content
    - _Requirements: 14.2_

  - [ ]* 24.2 Write property tests for keyboard navigation
    - **Property 66: Keyboard navigation works for all elements**
    - **Validates: Requirements 14.2**

  - [x] 24.3 Implement ARIA attributes
    - Add appropriate ARIA labels and roles
    - Implement live regions for dynamic content
    - _Requirements: 14.3, 14.5_

  - [ ]* 24.4 Write property tests for ARIA
    - **Property 67: ARIA attributes are present**
    - **Property 69: Dynamic content announces changes**
    - **Validates: Requirements 14.3, 14.5**

  - [x] 24.5 Implement visual accessibility
    - Ensure color contrast meets requirements
    - Add visible focus indicators
    - Support reduced motion preferences
    - Add alt text for non-text content
    - _Requirements: 14.4, 14.6, 14.7, 14.8_

  - [ ]* 24.6 Write property tests for visual accessibility
    - **Property 68: Color contrast meets requirements**
    - **Property 70: Focus indicators are visible**
    - **Property 71: Reduced motion is respected**
    - **Property 72: Non-text content has alternatives**
    - **Validates: Requirements 14.4, 14.6, 14.7, 14.8**

- [x] 25. Router Configuration
  - [x] 25.1 Configure React Router with dynamic routes
    - Set up router with routes from bootstrap config
    - Implement protected routes with auth check
    - Handle 404 and error routes
    - _Requirements: 1.2, 2.1_

  - [x] 25.2 Implement route guards
    - Check authentication before rendering protected routes
    - Check authorization based on page policies
    - Redirect to login when unauthenticated
    - _Requirements: 2.1, 2.2_

- [x] 26. Final Integration and Polish
  - [x] 26.1 Wire all components together
    - Connect all pages to router
    - Ensure navigation works correctly
    - Verify all API integrations
    - _Requirements: All_

  - [x] 26.2 Implement login and provider selection pages
    - Create login page with OIDC redirect
    - Create provider selection page for multiple providers
    - Handle authentication callback
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 26.3 Add loading states and transitions
    - Add page transition animations (respecting reduced motion)
    - Ensure consistent loading states across pages
    - _Requirements: 18.4_

- [x] 27. Final Checkpoint - All features complete
  - Ensure all tests pass, ask the user if questions arise.
  - Run full test suite including property tests
  - Verify accessibility compliance with automated tools
  - Test responsive layouts at all breakpoints

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- The application uses @emf/sdk for all API communication
- The application uses @emf/components for DataTable, ResourceForm, ResourceDetail, FilterBuilder, Navigation, and Layout components
- The application uses @emf/plugin-sdk for plugin integration
