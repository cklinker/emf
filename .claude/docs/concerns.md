# Codebase Concerns

## Security Risks

**SQL Injection in SupersetDatabaseUserService:**
- Direct SQL string concatenation with `String.format()` for user-controlled input
- File: `kelta-worker/.../service/SupersetDatabaseUserService.java` (lines 56-99)
- Fix: Use parameterized queries or stored procedures

**Gateway permitAll() Config:**
- `SecurityConfig` permits all exchanges; authorization depends entirely on gateway filters
- File: `kelta-gateway/.../config/SecurityConfig.java` (line 101)
- Fix: Defense-in-depth with explicit route authorization at service level

**Missing Auth on Internal Endpoints:**
- Worker controllers lack `@PreAuthorize`/`@Secured`; vulnerable if accessed directly
- Files: `InternalBootstrapController`, `AuthorizationTestController`, `MetricsController`
- Fix: Add authorization annotations to controller methods

**FlowConfig Schema Creation:**
- Minimal sanitization for dynamic schema names using regex
- File: `kelta-worker/.../config/FlowConfig.java` (lines 112-115)
- Fix: Validate tenant slug format at creation time

## Known Bugs

| Bug | File | Root Cause |
|-----|------|-----------|
| Federated users stuck as PENDING_ACTIVATION | `kelta-auth/.../federation/FederatedUserMapper.java` (174-178) | `lookupProfileId()` returns null (TODO: unimplemented) |
| Password reset sends no email | `kelta-auth/.../controller/PasswordController.java` (96) | Email sending TODO unimplemented |
| Unhandled EmptyResultDataAccessException on user not found | `PasswordController.java` (55-60) | Missing try-catch on `queryForObject()` |
| NPE if `reset_token_expires_at` is NULL | `PasswordController.java` (118-119) | Missing null check before Timestamp cast |

## Tech Debt

**Large files needing decomposition:**
| File | Lines | Fix |
|------|-------|-----|
| `SystemCollectionDefinitions.java` | 1,434 | Move to JSON/YAML config loaded at startup |
| `DynamicCollectionRouter.java` | 1,357 | Extract RouteResolutionHandler, InclusionHandler, FieldMapperHandler |
| `PhysicalTableStorageAdapter.java` | 1,091 | Extract SQL builder classes; separate concerns |

**Other:**
- Hardcoded `emf_control_plane` DB name in `SupersetDatabaseUserService.java` (line 78)
- Potential N+1 in `SearchIndexService.java` during reindex (per-collection queries)

## Fragile Areas

- **FK constraint generation** (`PhysicalTableStorageAdapter.java` 177-185): String concatenation for FK names; collision risk
- **Tenant schema isolation** (`PhysicalTableStorageAdapter.java` 105-107): Assumes schema exists; silent failure on permission error

## Dependency Risks

- Multiple BOM version overrides in worker POM increase transitive conflict risk (`kelta-worker/pom.xml` 27-32)

## Test Coverage Gaps

- Federated user provisioning (`FederatedUserMapper`) — no tests
- Password reset workflow end-to-end — no tests
- Schema migration edge cases (`SchemaMigrationEngine` — 771 lines) — minimal tests
- SQL filter operator mappings in `PhysicalTableStorageAdapter` — no tests
