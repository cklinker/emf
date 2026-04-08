# kelta-auth

Internal OIDC provider for the Kelta platform. OAuth2 Authorization Server with identity brokering, MFA, and session management.

## Package Layout

```
io.kelta.auth/
  config/          ← AuthorizationServerConfig, AuthProperties, CORS, redirect URI validation
  controller/      ← Thymeleaf controllers (login, consent, MFA, password) + API controllers
  federation/      ← External IdP brokering (dynamic client registration, user mapping)
  model/           ← KeltaUserDetails, KeltaSession, KeltaUserDetailsMixin
  service/         ← Business logic (token customizer, user details, TOTP, session, worker client, etc.)
```

## Key Patterns

### Models
- `KeltaUserDetails` — implements Spring `UserDetails`, has 10-param constructor
- `KeltaSession` — serializable session data stored in Redis
- Both are plain classes (not records, not JPA entities)

### OAuth2 Flow
1. Client redirects to `/oauth2/authorize`
2. `LoginController` renders Thymeleaf login page
3. `KeltaUserDetailsService` loads user from worker via `WorkerClient`
4. `KeltaTokenCustomizer` adds custom claims (tenantId, profileId, groups)
5. Token issued and returned to client

### Identity Brokering (SSO)
- `DynamicClientRegistrationRepository` — loads OIDC provider configs from worker at runtime
- `FederatedLoginSuccessHandler` — handles successful external IdP login
- `FederatedUserMapper` — maps external identity to Kelta user

### MFA
- `TotpService` — TOTP generation and verification
- `MfaController` — enrollment and challenge pages
- Session attributes: `SESSION_MFA_USER_ID`, `SESSION_MFA_PENDING`, `SESSION_MFA_SETUP_REQUIRED`

### Error Handling
Standard Spring Security exceptions (`AuthenticationException` hierarchy). No custom exception classes in this service.

## When Adding a New Auth Flow

1. Add a `@Controller` in `controller/` for the page/form handling
2. Create a Thymeleaf template in `src/main/resources/templates/`
3. Add business logic to an existing or new service in `service/`
4. Wire into `AuthorizationServerConfig` or `SecurityConfig` if it changes the security filter chain
5. Add a test in `src/test/java/io/kelta/auth/`

**Reference**: `MfaController.java` + `TotpService.java`

## Reference Implementations

| Pattern | File |
|---------|------|
| Thymeleaf controller | `controller/LoginController.java` |
| API controller | `controller/SessionController.java` |
| OAuth2 customization | `service/KeltaTokenCustomizer.java` |
| User loading | `service/KeltaUserDetailsService.java` |
| External IdP | `federation/DynamicClientRegistrationRepository.java` |
| MFA | `service/TotpService.java` + `controller/MfaController.java` |
| Worker HTTP client | `service/WorkerClient.java` |

## Running Tests

```bash
mvn test -f kelta-auth/pom.xml                                      # All tests
mvn test -f kelta-auth/pom.xml -Dtest=LoginControllerTest           # Single class
mvn test -f kelta-auth/pom.xml -Dtest=LoginControllerTest#shouldRenderLoginPage  # Single method
mvn test -f kelta-auth/pom.xml -Dtest="*Mfa*"                       # Pattern match
```

## Test Fixtures

Use `TestFixtures.java` in `src/test/java/io/kelta/auth/` for pre-built `KeltaUserDetails` and `KeltaSession` instances. Prefer these over hand-constructing models so tests stay terse and survive constructor changes.
