# Requirements Document

## Introduction

This document specifies the requirements for implementing configurable JWT claim mapping for OIDC providers in the EMF platform. Different OIDC providers (Keycloak, Auth0, Okta, Authentik, etc.) use different claim names in their JWT tokens for the same information. This feature will allow administrators to configure how JWT claims are mapped to the EMF platform's internal user attributes, enabling the platform to work with any OIDC provider regardless of their token structure.

## Glossary

- **OIDC_Provider**: An OpenID Connect identity provider configuration entity in the EMF platform
- **JWT_Token**: JSON Web Token issued by an OIDC provider containing user claims
- **Claim_Path**: A string representing the location of a claim in a JWT token (e.g., "roles", "realm_access.roles")
- **Role_Mapping**: A JSON object that maps external role values from the OIDC provider to internal EMF role names
- **Control_Plane**: The EMF backend service that manages OIDC provider configurations
- **Admin_UI**: The React-based administrative interface for managing OIDC providers
- **Package_Export**: The process of exporting OIDC provider configurations for migration between environments

## Requirements

### Requirement 1: Database Schema Extension

**User Story:** As a platform administrator, I want the system to persist claim mapping configurations, so that OIDC provider settings are maintained across restarts.

#### Acceptance Criteria

1. THE Control_Plane SHALL add a `roles_claim` column to the `oidc_provider` table with VARCHAR(200) type
2. THE Control_Plane SHALL add a `roles_mapping` column to the `oidc_provider` table with TEXT type for storing JSON
3. THE Control_Plane SHALL add an `email_claim` column to the `oidc_provider` table with VARCHAR(200) type
4. THE Control_Plane SHALL add a `username_claim` column to the `oidc_provider` table with VARCHAR(200) type
5. THE Control_Plane SHALL add a `name_claim` column to the `oidc_provider` table with VARCHAR(200) type
6. WHEN the database migration runs, THE Control_Plane SHALL set default values for existing providers (email_claim='email', username_claim='preferred_username', name_claim='name')

### Requirement 2: Entity Layer Updates

**User Story:** As a developer, I want the OidcProvider entity to include claim mapping fields, so that I can work with claim configurations in the application code.

#### Acceptance Criteria

1. THE OidcProvider entity SHALL include a `rolesClaim` field with appropriate getter and setter methods
2. THE OidcProvider entity SHALL include a `rolesMapping` field with appropriate getter and setter methods
3. THE OidcProvider entity SHALL include an `emailClaim` field with appropriate getter and setter methods
4. THE OidcProvider entity SHALL include a `usernameClaim` field with appropriate getter and setter methods
5. THE OidcProvider entity SHALL include a `nameClaim` field with appropriate getter and setter methods
6. WHEN an OidcProvider entity is created, THE Control_Plane SHALL initialize claim fields with sensible defaults

### Requirement 3: DTO Layer Updates

**User Story:** As a developer, I want DTOs to include claim mapping fields, so that claim configurations can be transmitted via the API.

#### Acceptance Criteria

1. THE OidcProviderDto SHALL include fields for all claim mapping configurations
2. THE AddOidcProviderRequest SHALL include optional fields for claim mapping configurations
3. THE UpdateOidcProviderRequest SHALL include optional fields for claim mapping configurations
4. WHEN converting from entity to DTO, THE Control_Plane SHALL include all claim mapping fields
5. THE PackageDto.PackageOidcProviderDto SHALL include claim mapping fields for export/import functionality

### Requirement 4: Service Layer Validation

**User Story:** As a platform administrator, I want the system to validate claim configurations, so that invalid configurations are rejected before persistence.

#### Acceptance Criteria

1. WHEN a claim path is provided, THE Control_Plane SHALL validate it is not empty and does not exceed 200 characters
2. WHEN a roles_mapping is provided, THE Control_Plane SHALL validate it is valid JSON or null
3. IF a roles_mapping contains invalid JSON, THEN THE Control_Plane SHALL return a validation error with a descriptive message
4. WHEN creating or updating an OIDC provider, THE Control_Plane SHALL apply default values for claim fields if not provided
5. THE Control_Plane SHALL accept nested claim paths using dot notation (e.g., "realm_access.roles")

### Requirement 5: API Endpoint Updates

**User Story:** As a platform administrator, I want to configure claim mappings through the API, so that I can manage OIDC providers programmatically.

#### Acceptance Criteria

1. WHEN creating an OIDC provider via POST /control/oidc/providers, THE Control_Plane SHALL accept claim mapping fields in the request body
2. WHEN updating an OIDC provider via PUT /control/oidc/providers/{id}, THE Control_Plane SHALL accept claim mapping fields in the request body
3. WHEN retrieving OIDC providers via GET /control/oidc/providers, THE Control_Plane SHALL include claim mapping fields in the response
4. WHEN claim mapping fields are omitted in a request, THE Control_Plane SHALL use default values
5. THE Control_Plane SHALL return appropriate error responses for invalid claim configurations

### Requirement 6: Admin UI Form Updates

**User Story:** As a platform administrator, I want to configure claim mappings through the Admin UI, so that I can set up OIDC providers without writing code.

#### Acceptance Criteria

1. WHEN creating or editing an OIDC provider, THE Admin_UI SHALL display input fields for roles_claim, email_claim, username_claim, and name_claim
2. WHEN creating or editing an OIDC provider, THE Admin_UI SHALL display a textarea for roles_mapping JSON configuration
3. THE Admin_UI SHALL provide placeholder text with examples for each claim field
4. THE Admin_UI SHALL provide helpful hints explaining what each claim field does
5. WHEN a user submits the form, THE Admin_UI SHALL validate that roles_mapping is valid JSON if provided
6. THE Admin_UI SHALL display validation errors inline for invalid claim configurations
7. WHEN displaying an existing provider, THE Admin_UI SHALL populate claim mapping fields with current values

### Requirement 7: Default Values and Examples

**User Story:** As a platform administrator, I want sensible defaults and examples for claim mappings, so that I can quickly configure common OIDC providers.

#### Acceptance Criteria

1. THE Control_Plane SHALL use "email" as the default value for email_claim
2. THE Control_Plane SHALL use "preferred_username" as the default value for username_claim
3. THE Control_Plane SHALL use "name" as the default value for name_claim
4. THE Admin_UI SHALL provide example claim paths in placeholder text (e.g., "roles, realm_access.roles, groups")
5. THE Admin_UI SHALL provide an example roles_mapping JSON structure in the hint text

### Requirement 8: Backward Compatibility

**User Story:** As a platform operator, I want existing OIDC providers to continue working after the upgrade, so that authentication is not disrupted.

#### Acceptance Criteria

1. WHEN the database migration runs, THE Control_Plane SHALL preserve all existing OIDC provider configurations
2. WHEN an existing OIDC provider has null claim fields, THE Control_Plane SHALL apply default values at runtime
3. THE Control_Plane SHALL continue to support OIDC providers created before this feature was added
4. WHEN retrieving an existing provider via API, THE Control_Plane SHALL return default claim values if fields are null

### Requirement 9: Package Export and Import

**User Story:** As a platform operator, I want claim mapping configurations to be included in package exports, so that I can migrate OIDC providers between environments.

#### Acceptance Criteria

1. WHEN exporting a package, THE Control_Plane SHALL include all claim mapping fields in the exported OIDC provider data
2. WHEN importing a package, THE Control_Plane SHALL restore all claim mapping fields for OIDC providers
3. WHEN importing a package with missing claim fields, THE Control_Plane SHALL apply default values
4. THE Control_Plane SHALL validate claim configurations during package import

### Requirement 10: Testing Coverage

**User Story:** As a developer, I want comprehensive tests for claim mapping functionality, so that the feature works reliably.

#### Acceptance Criteria

1. THE Control_Plane SHALL include unit tests for entity persistence with claim mapping fields
2. THE Control_Plane SHALL include unit tests for service layer validation of claim configurations
3. THE Control_Plane SHALL include unit tests for DTO conversion with claim mapping fields
4. THE Control_Plane SHALL include integration tests for API endpoints with claim mapping data
5. THE Admin_UI SHALL include tests for form validation of claim mapping fields
6. THE Admin_UI SHALL include tests for form submission with claim mapping data
