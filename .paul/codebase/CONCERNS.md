# Codebase Concerns

**Analysis Date:** 2026-03-22

## Security Considerations

**SQL Injection Risk in SupersetDatabaseUserService:**
- Risk: Direct SQL string concatenation with `String.format()` for user-controlled input (passwords)
- Files: `kelta-worker/src/main/java/io/kelta/service/SupersetDatabaseUserService.java` (lines 56-99)
- Current mitigation: Identifier quoting with double quotes
- Recommendations: Use parameterized queries or stored procedures; never log passwords

**Gateway permitAll() Security Config:**
- Risk: `SecurityConfig` permits all exchanges; authorization depends entirely on gateway filters
- Files: `kelta-gateway/src/main/java/io/kelta/config/SecurityConfig.java` (line 101)
- Current mitigation: Gateway filter chain handles auth
- Recommendations: Defense-in-depth with explicit route authorization at service level

**Missing Auth Annotations on Internal Endpoints:**
- Risk: Worker controllers lack `@PreAuthorize`/`@Secured`; vulnerable if accessed directly
- Files: `kelta-worker/src/main/java/io/kelta/controller/InternalBootstrapController.java`, `kelta-worker/src/main/java/io/kelta/controller/AuthorizationTestController.java`, `kelta-worker/src/main/java/io/kelta/controller/MetricsController.java`
- Recommendations: Add authorization annotations to controller methods

**FlowConfig Schema Creation:**
- Risk: Minimal sanitization for dynamic schema names using regex
- Files: `kelta-worker/src/main/java/io/kelta/config/FlowConfig.java` (lines 112-115)
- Recommendations: Validate tenant slug format at creation time; use fully qualified names

## Known Bugs

**Incomplete OAuth2 Profile Resolution:**
- Symptoms: Federated users created as `PENDING_ACTIVATION`, unable to log in
- Trigger: OIDC authentication with group-based profile mapping
- Files: `kelta-auth/src/main/java/io/kelta/auth/federation/FederatedUserMapper.java` (lines 174-178)
- Root cause: `lookupProfileId()` returns null (TODO: unimplemented)
- Fix: Implement profile lookup against WorkerClient

**Missing Password Reset Email:**
- Symptoms: Users requesting password reset receive no email
- Files: `kelta-auth/src/main/java/io/kelta/auth/controller/PasswordController.java` (line 96)
- Root cause: Email sending TODO unimplemented
- Fix: Integrate with email service

**Error Handling Gap in PasswordController:**
- Symptoms: Unhandled `EmptyResultDataAccessException` if user not found
- Files: `kelta-auth/src/main/java/io/kelta/auth/controller/PasswordController.java` (lines 55-60)
- Fix: Wrap `queryForObject()` with try-catch or use default value

**Null Pointer in Password Reset:**
- Symptoms: NPE if `reset_token_expires_at` is NULL in database
- Files: `kelta-auth/src/main/java/io/kelta/auth/controller/PasswordController.java` (lines 118-119)
- Fix: Add null check before Timestamp cast

## Tech Debt

**Large Files Needing Decomposition:**
- `SystemCollectionDefinitions.java` (1,434 lines) - Configuration data in Java code
  - Files: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/model/system/SystemCollectionDefinitions.java`
  - Fix: Move to JSON/YAML config files loaded at startup
- `DynamicCollectionRouter.java` (1,357 lines) - Multiple concerns mixed
  - Files: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/router/DynamicCollectionRouter.java`
  - Fix: Extract RouteResolutionHandler, InclusionHandler, FieldMapperHandler
- `PhysicalTableStorageAdapter.java` (1,091 lines) - SQL generation + schema management
  - Files: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/storage/PhysicalTableStorageAdapter.java`
  - Fix: Extract SQL builder classes; separate concerns

**Hardcoded Database Name:**
- Issue: `emf_control_plane` hardcoded in SupersetDatabaseUserService
- Files: `kelta-worker/src/main/java/io/kelta/service/SupersetDatabaseUserService.java` (line 78)
- Fix: Externalize as application.yml property

## Performance Bottlenecks

**Potential N+1 in Search Reindexing:**
- Problem: Collection iteration with per-collection queries during reindex
- Files: `kelta-worker/src/main/java/io/kelta/service/SearchIndexService.java`
- Fix: Batch queries for efficiency

## Fragile Areas

**Foreign Key Constraint Generation:**
- Files: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/storage/PhysicalTableStorageAdapter.java` (lines 177-185)
- Why fragile: String concatenation for FK names; collision risk with similar field names
- Fix: Use UUID or fully qualified constraint names

**Tenant Schema Isolation:**
- Files: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/storage/PhysicalTableStorageAdapter.java` (lines 105-107)
- Why fragile: Assumes schema exists before table creation; silent failure on permission error
- Fix: Explicit schema existence check with clear error

## Dependencies at Risk

**Spring Boot 3.2.2:**
- Risk: Released early 2024; may be missing security patches
- Files: `kelta-platform/pom.xml` (line 26)
- Action: Update to latest Spring Boot 3.x; review security advisories

**Multiple BOM Version Overrides:**
- Risk: Manual version overrides in worker POM increase transitive conflict risk
- Files: `kelta-worker/pom.xml` (lines 27-32)
- Action: Audit dependency tree; consolidate BOMs

## Test Coverage Gaps

**Critical Paths Without Tests:**
- Federated user provisioning (`FederatedUserMapper`) - no tests
- Password reset workflow end-to-end
- Schema migration edge cases (`SchemaMigrationEngine` - 771 lines)
- SQL filter operator mappings in `PhysicalTableStorageAdapter`
- Overall: ~172 test files across 15,351 source files (1.1% file ratio)

## Documentation Gaps

**Complex Query Engine:**
- Files: `kelta-platform/runtime/runtime-core/src/main/java/io/kelta/query/DefaultQueryEngine.java` (721 lines)
- Missing: Method-level JavaDoc for `computeVirtualFields()`, `decryptFields()`, filter coercion

**Flow Engine Thread Pool:**
- Files: `kelta-worker/src/main/java/io/kelta/config/FlowConfig.java`
- Missing: Tuning documentation for `kelta.flow.executor.pool-size` property

---

*Concerns audit: 2026-03-22*
*Update as issues are fixed or new ones discovered*
