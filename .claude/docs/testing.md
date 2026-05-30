# Testing Patterns

## Java Testing

### Frameworks
- **JUnit 5** (Jupiter) via Spring Boot
- **AssertJ** fluent assertions
- **Mockito** 5.21.0
- **jqwik** 1.8.2 — property-based testing
- **Testcontainers** 1.20.4 — Docker-based integration tests
- **MockWebServer** — HTTP service mocks
- **Awaitility** — async assertions

### Integration harness (`kelta-test-harness`)

Boots a full mini-stack (Postgres, Redis, NATS, Cerbos, worker, auth, gateway) via Testcontainers and runs `*ScenarioTest.java` against it under the `integration-tests` profile.

**Database selection**: `KeltaStack` reads `CI_DB_JDBC_URL` at startup. When set (CI), it skips the Testcontainers PG and points worker + auth at the shared `kelta-ci-db` pool — the URL emitted by `scripts/ci/checkout-db.sh` already pins `currentSchema=ci_<run-tag>` for per-run isolation. When unset (local dev), it falls back to a Testcontainers PG on the docker network alias `postgres`. The Testcontainers dependency stays in `pom.xml` either way.

### Organization
- Unit tests: `src/test/java/io/kelta/...Test.java`
- Test resources: `src/test/resources/`
- No integration tests in gateway (skipITs = true)
- Naming: append `Test` to class name

### Structure
```java
@DisplayName("GatewayMetrics Tests")
class GatewayMetricsTest {
    private SimpleMeterRegistry registry;
    private GatewayMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new GatewayMetrics(registry);
    }

    @Nested
    @DisplayName("kelta.gateway.requests (Timer)")
    class RequestTimer {
        @Test
        @DisplayName("Should record request duration with all tags")
        void shouldRecordRequestDuration() {
            // arrange, act, assert
        }
    }
}
```

**Patterns**: `@DisplayName` for readable names, `@Nested` for grouping, `@BeforeEach` for setup, method names `shouldXxxWhenYyy()`, AssertJ `assertThat(actual).isEqualTo(expected)`

### Mocking
- MockWebServer for HTTP service mocks
- MockServerHttpRequest/Exchange for gateway filter tests
- Immutable test data: `List.of(...)` for constants
- Focus on behavior over state
- `Mockito.inOrder(mockA, mockB)` when the bug is about *ordering* of side-effects across collaborators (e.g. "reconcile schema must run before FK constraint statements"). Plain `verify()` checks only that calls happened, not the sequence — `InOrder` is what catches re-ordering regressions. See `PhysicalTableStorageAdapterSystemCollectionTest.initializeCollection_reconcilesSchema_beforeForeignKeyStatements` for an example.
- `doThrow(new DuplicateKeyException(...)).when(mockJdbc).execute(argThat(sql -> sql.contains("CREATE TABLE")))` to simulate PostgreSQL-only failure modes (e.g. the `pg_type_typname_nsp_index` race in concurrent `CREATE TABLE IF NOT EXISTS`) without spinning up Testcontainers. H2 won't reproduce these, so a mocked `JdbcTemplate` that throws the translated Spring exception on a matching statement is the cheapest regression guard. See `PhysicalTableStorageAdapterSystemCollectionTest.initializeCollection_swallowsDuplicateKey_fromConcurrentCreateRace`.

## TypeScript Testing

### Frameworks
- **Vitest** — 1.3.1 (kelta-web), 4.0.18 (kelta-ui)
- **jsdom** — DOM environment
- **React Testing Library** — 14.2.1 (kelta-web), 16.3.2 (kelta-ui)
- **MSW** — 2.2.1 (kelta-web), 2.12.7 (kelta-ui)
- **fast-check** — 3.15.1 (kelta-web), 4.5.3 (kelta-ui)
- **Playwright** 1.50.0 — E2E testing (`e2e-tests/`)

### Organization
- Co-located: `FileName.test.ts` alongside source
- Property tests: `FileName.property.test.ts`
- Coverage thresholds: 80% (branches, functions, lines, statements) for kelta-web

### Structure
```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

describe('ResourceClient', () => {
  let client: KeltaClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new KeltaClient({ baseUrl: 'https://api.example.com' });
  });

  it('should list resources', async () => {
    // arrange, act, assert
  });
});
```

### Mocking
```typescript
// Module mocking
vi.mock('axios', async () => {
  const actual = await vi.importActual('axios');
  return { ...actual, default: { create: vi.fn(() => createMockAxiosInstance()) } };
});
```

### Component Testing
```typescript
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

it('should add filter when button clicked', async () => {
  const user = userEvent.setup();
  render(<FilterBuilder fields={mockFields} value={[]} onChange={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /add/i }));
  expect(onChange).toHaveBeenCalled();
});
```
