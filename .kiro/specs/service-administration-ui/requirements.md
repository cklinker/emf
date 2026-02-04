# Requirements Document

## Introduction

This specification defines enhancements to the EMF UI to integrate service administration capabilities into the collections management workflow. The control plane API already provides full CRUD endpoints for services at `/control/services`, and a complete ServicesPage component exists. The CollectionDto API response includes `serviceId` and `serviceName` fields. This feature focuses on displaying service information in the CollectionsPage and enabling navigation between collections and their associated services.

## Glossary

- **Service**: A domain microservice in the EMF platform that owns collections and provides business logic
- **Collection**: A data entity managed by a service, representing a table or JSONB storage
- **CollectionsPage**: The React component that displays a paginated list of all collections
- **ServicesPage**: The existing React component that provides full CRUD operations for services
- **Service_Filter**: A UI control that allows filtering collections by their associated service
- **Service_Column**: A table column in the CollectionsPage that displays the service name for each collection
- **Navigation_Link**: A clickable element that navigates the user to a different page

## Requirements

### Requirement 1: Service Information Display

**User Story:** As an administrator, I want to see which service owns each collection, so that I can understand the system architecture and data ownership.

#### Acceptance Criteria

1. WHEN viewing the collections table, THE CollectionsPage SHALL display a "Service" column showing the service name for each collection
2. WHEN a collection has an associated service, THE Service_Column SHALL display the service's display name or name
3. WHEN a collection has no associated service, THE Service_Column SHALL display a placeholder indicating no service
4. WHEN the collections table is rendered, THE Service_Column SHALL be positioned between the "Display Name" and "Status" columns

### Requirement 2: Service Navigation

**User Story:** As an administrator, I want to click on a service name in the collections table, so that I can quickly navigate to view or edit that service's details.

#### Acceptance Criteria

1. WHEN a service name is displayed in the Service_Column, THE UI SHALL render it as a clickable link
2. WHEN a user clicks a service name link, THE UI SHALL navigate to the ServicesPage
3. WHEN navigating to the ServicesPage from a service link, THE UI SHALL maintain the application state and navigation history
4. WHEN a collection has no associated service, THE Service_Column SHALL not display a clickable link

### Requirement 3: Service Filtering

**User Story:** As an administrator, I want to filter collections by service, so that I can focus on collections belonging to a specific service.

#### Acceptance Criteria

1. WHEN viewing the CollectionsPage, THE UI SHALL display a service filter dropdown in the filters section
2. WHEN the service filter dropdown is opened, THE UI SHALL display all available services plus an "All Services" option
3. WHEN a user selects a service from the filter, THE CollectionsPage SHALL display only collections belonging to that service
4. WHEN a user selects "All Services", THE CollectionsPage SHALL display all collections regardless of service
5. WHEN the service filter is applied, THE pagination SHALL reset to page 1

### Requirement 4: Collection Detail Service Display

**User Story:** As an administrator, I want to see service information in the collection detail view, so that I have complete context when viewing a single collection.

#### Acceptance Criteria

1. WHEN viewing a collection detail page, THE UI SHALL display the associated service name
2. WHEN the service name is displayed in the detail view, THE UI SHALL render it as a clickable link to the ServicesPage
3. WHEN a collection has no associated service, THE detail view SHALL display a placeholder indicating no service

### Requirement 5: Internationalization Support

**User Story:** As an administrator using the UI in different languages, I want all service-related UI text to be properly translated, so that I can use the application in my preferred language.

#### Acceptance Criteria

1. WHEN service-related UI elements are rendered, THE UI SHALL use i18n translation keys for all text
2. WHEN new translation keys are added, THE UI SHALL include them in the English translation file
3. THE UI SHALL include translation keys for: "Service", "All Services", "Select a service", and "No service"

### Requirement 6: Accessibility Compliance

**User Story:** As an administrator using assistive technology, I want service-related UI elements to be accessible, so that I can effectively use the service administration features.

#### Acceptance Criteria

1. WHEN service links are rendered, THE UI SHALL include appropriate ARIA labels describing the link purpose
2. WHEN the service filter dropdown is rendered, THE UI SHALL include proper label associations and ARIA attributes
3. WHEN keyboard navigation is used, THE service links and filter SHALL be fully operable via keyboard
4. WHEN screen readers are used, THE service column header SHALL be properly announced as a table column header

### Requirement 7: ServicesPage Integration Verification

**User Story:** As an administrator, I want to access the services management page from the main navigation, so that I can manage services independently of collections.

#### Acceptance Criteria

1. WHEN the application loads, THE ServicesPage SHALL be registered in the application routing
2. WHEN a user navigates to `/services`, THE UI SHALL display the ServicesPage component
3. WHEN the ServicesPage is displayed, THE UI SHALL show the complete service list with CRUD operations
4. WHEN the main navigation menu is rendered, THE UI SHALL include a "Services" menu item that links to `/services`

### Requirement 8: Data Fetching and State Management

**User Story:** As an administrator, I want service data to be efficiently fetched and cached, so that the UI remains responsive and doesn't make unnecessary API calls.

#### Acceptance Criteria

1. WHEN the CollectionsPage loads, THE UI SHALL fetch the list of services for the filter dropdown
2. WHEN service data is fetched, THE UI SHALL use TanStack Query for caching and state management
3. WHEN collections are displayed, THE UI SHALL use the serviceId and serviceName fields from the CollectionDto response
4. WHEN the services list is already cached, THE UI SHALL not make redundant API calls
