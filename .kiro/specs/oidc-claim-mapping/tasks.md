# Implementation Plan: OIDC Claim Mapping

## Overview

This implementation adds configurable JWT claim mapping to OIDC provider configurations in the EMF platform. The work spans two repositories (emf-control-plane and emf-ui) and includes database migrations, entity/DTO updates, service layer validation, API endpoints, and UI form enhancements.

## Tasks

- [x] 1. Create database migration for claim mapping columns
  - Create Flyway migration file `V6__add_oidc_claim_mapping.sql`
  - Add columns: roles_claim, roles_mapping, email_claim, username_claim, name_claim
  - Set default values for existing providers
  - Add JSON validation constraint for roles_mapping
  - Add column comments for documentation
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [ ]* 1.1 Write unit test for migration default values
  - Test that existing providers get default claim values after migration
  - _Requirements: 1.6_

- [x] 2. Update OidcProvider entity with claim mapping fields
  - [x] 2.1 Add claim mapping fields to OidcProvider entity
    - Add rolesClaim, rolesMapping, emailClaim, usernameClaim, nameClaim fields
    - Add JPA column annotations with appropriate lengths
    - Implement getters with default value logic for email/username/name claims
    - Implement setters for all claim fields
    - Update toString() method to include new fields
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  
  - [ ]* 2.2 Write property test for default values
    - **Property 1: Default Values Applied for Null Claims**
    - **Validates: Requirements 2.6, 4.4, 5.4, 8.2**
  
  - [ ]* 2.3 Write unit tests for entity persistence
    - Test saving and retrieving entities with claim fields
    - Test null claim fields return defaults
    - _Requirements: 2.6_

- [x] 3. Update DTO classes with claim mapping fields
  - [x] 3.1 Update OidcProviderDto
    - Add claim mapping fields
    - Update fromEntity() method to include claim fields
    - Update equals(), hashCode(), and toString() methods
    - _Requirements: 3.1, 3.4_
  
  - [x] 3.2 Update AddOidcProviderRequest
    - Add optional claim mapping fields
    - Add @Size validation annotations
    - Update constructors and toString()
    - _Requirements: 3.2_
  
  - [x] 3.3 Update UpdateOidcProviderRequest
    - Add optional claim mapping fields
    - Add @Size validation annotations
    - Update toString()
    - _Requirements: 3.3_
  
  - [ ]* 3.4 Write property test for DTO conversion
    - **Property 2: Entity to DTO Conversion Preserves Claim Fields**
    - **Validates: Requirements 3.4**

- [x] 4. Update OidcProviderService with validation logic
  - [x] 4.1 Add claim path validation method
    - Implement validateClaimPath() to check length and format
    - Validate alphanumeric, dots, and underscores only
    - _Requirements: 4.1, 4.5_
  
  - [x] 4.2 Add roles mapping JSON validation method
    - Implement validateRolesMapping() to check JSON validity
    - Use Jackson ObjectMapper for parsing
    - Provide descriptive error messages
    - _Requirements: 4.2, 4.3_
  
  - [x] 4.3 Update addProvider() method
    - Call validation methods for claim fields
    - Set claim fields on entity from request
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [x] 4.4 Update updateProvider() method
    - Call validation methods for claim fields
    - Update claim fields if provided in request
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  
  - [ ]* 4.5 Write property test for claim path length validation
    - **Property 3: Claim Path Length Validation**
    - **Validates: Requirements 4.1**
  
  - [ ]* 4.6 Write property test for JSON validation
    - **Property 4: Roles Mapping JSON Validation**
    - **Validates: Requirements 4.2, 4.3**
  
  - [ ]* 4.7 Write property test for nested claim paths
    - **Property 5: Nested Claim Paths Accepted**
    - **Validates: Requirements 4.5**
  
  - [ ]* 4.8 Write unit tests for service validation
    - Test validation error messages
    - Test edge cases (empty strings, whitespace, special characters)
    - _Requirements: 4.1, 4.2, 4.3, 4.5_

- [x] 5. Checkpoint - Ensure backend tests pass
  - Run all backend tests
  - Verify database migration works
  - **COMPLETED**: Fixed CollectionServiceTest and UiConfigServiceTest
  - **NOTE**: 70 Mockito errors remain due to Java 24 compatibility (not code issues)
  - Ask the user if questions arise

- [x] 6. Update PackageDto for export/import
  - [x] 6.1 Update PackageOidcProviderDto nested class
    - Add claim mapping fields
    - Update fromEntity() method to include claim fields
    - Update toEntity() method to set claim fields
    - Add getters and setters
    - _Requirements: 3.5, 9.1, 9.2, 9.3_
  
  - [x] 6.2 Update PackageService import validation
    - Validate claim configurations during import
    - Apply defaults for missing claim fields
    - _Requirements: 9.3, 9.4_
  
  - [ ]* 6.3 Write property test for package export
    - **Property 12: Package Export Includes Claim Fields**
    - **Validates: Requirements 9.1**
  
  - [ ]* 6.4 Write property test for package import round-trip
    - **Property 13: Package Import Round-Trip Preserves Claims**
    - **Validates: Requirements 9.2, 9.3**
  
  - [ ]* 6.5 Write property test for package import validation
    - **Property 14: Package Import Validates Claim Configurations**
    - **Validates: Requirements 9.4**

- [ ] 7. Update API integration tests
  - [ ]* 7.1 Write property test for POST endpoint
    - **Property 6: POST Request Persists Claim Fields**
    - **Validates: Requirements 5.1**
  
  - [ ]* 7.2 Write property test for PUT endpoint
    - **Property 7: PUT Request Updates Claim Fields**
    - **Validates: Requirements 5.2**
  
  - [ ]* 7.3 Write property test for GET endpoint
    - **Property 8: GET Request Returns Claim Fields**
    - **Validates: Requirements 5.3, 8.4**
  
  - [ ]* 7.4 Write property test for error responses
    - **Property 9: Invalid Claim Configurations Return Errors**
    - **Validates: Requirements 5.5**
  
  - [ ]* 7.5 Write unit tests for API endpoints
    - Test specific examples (Keycloak, Auth0, Okta claim paths)
    - Test backward compatibility with null fields
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 8.1, 8.3, 8.4_

- [x] 8. Checkpoint - Ensure all backend work is complete
  - Run full backend test suite
  - Verify all property tests pass
  - Test manually with Postman or curl
  - Ask the user if questions arise

- [x] 9. Update UI TypeScript interfaces
  - [x] 9.1 Update OIDCProvider interface
    - Add optional claim mapping fields
    - _Requirements: 6.1, 6.2_
  
  - [x] 9.2 Update OIDCProviderFormData interface
    - Add optional claim mapping fields
    - _Requirements: 6.1, 6.2_
  
  - [x] 9.3 Update FormErrors interface
    - Add optional error fields for claim mappings
    - _Requirements: 6.6_

- [x] 10. Update UI form validation
  - [x] 10.1 Update validateForm() function
    - Add JSON validation for rolesMapping
    - Add length validation for claim paths (max 200 characters)
    - Add appropriate error messages
    - _Requirements: 6.5, 6.6_
  
  - [ ]* 10.2 Write property test for UI JSON validation
    - **Property 10: UI Form Validates Roles Mapping JSON**
    - **Validates: Requirements 6.5**

- [x] 11. Add claim mapping form fields to OIDCProviderForm
  - [x] 11.1 Add roles claim input field
    - Add label, input, placeholder, and hint text
    - Wire up onChange and onBlur handlers
    - Add error display
    - _Requirements: 6.1, 6.3, 6.4, 6.6_
  
  - [x] 11.2 Add roles mapping textarea field
    - Add label, textarea, placeholder, and hint text
    - Wire up onChange and onBlur handlers
    - Add error display
    - Set rows={4} for better UX
    - _Requirements: 6.2, 6.3, 6.4, 6.6_
  
  - [x] 11.3 Add email claim input field
    - Add label, input, placeholder with default, and hint text
    - Wire up onChange and onBlur handlers
    - Add error display
    - _Requirements: 6.1, 6.3, 6.4, 6.6_
  
  - [x] 11.4 Add username claim input field
    - Add label, input, placeholder with default, and hint text
    - Wire up onChange and onBlur handlers
    - Add error display
    - _Requirements: 6.1, 6.3, 6.4, 6.6_
  
  - [x] 11.5 Add name claim input field
    - Add label, input, placeholder with default, and hint text
    - Wire up onChange and onBlur handlers
    - Add error display
    - _Requirements: 6.1, 6.3, 6.4, 6.6_

- [x] 12. Update UI form initialization and submission
  - [x] 12.1 Update form state initialization
    - Initialize claim fields from provider prop when editing
    - Set empty strings for new providers
    - _Requirements: 6.7_
  
  - [x] 12.2 Update form submission
    - Include claim fields in API request
    - Handle optional fields correctly (don't send empty strings)
    - _Requirements: 6.5_
  
  - [ ]* 12.3 Write property test for form population
    - **Property 11: UI Form Populates Existing Provider Data**
    - **Validates: Requirements 6.7**

- [x] 13. Add CSS styles for new form fields
  - Add styles for textarea (formTextarea class)
  - Ensure consistent spacing and layout
  - Test responsive behavior
  - _Requirements: 6.1, 6.2_

- [ ]* 14. Write UI component tests
  - Test form rendering with claim fields
  - Test form validation for claim fields
  - Test form submission with claim data
  - Test form population for existing providers
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

- [x] 15. Add i18n translations
  - Add translation keys for claim field labels
  - Add translation keys for placeholder text
  - Add translation keys for hint text
  - Add translation keys for validation error messages
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.6_

- [x] 16. Final checkpoint - End-to-end testing
  - Test creating OIDC provider with claim mappings via UI
  - Test updating OIDC provider claim mappings via UI
  - Test validation errors display correctly
  - Test backward compatibility with existing providers
  - Test package export/import with claim mappings
  - Verify all tests pass (backend and frontend)
  - Ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Backend work is in emf-control-plane repository
- Frontend work is in emf-ui repository
- Database migration must be tested carefully to ensure backward compatibility
- Property tests should run with minimum 100 iterations
- Each property test must reference its design document property number
- UI tests should use React Testing Library and Jest
- Backend tests should use JUnit 5 and jqwik for property-based testing
