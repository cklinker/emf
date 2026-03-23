# Testing Patterns

**Analysis Date:** 2026-03-22

## Java Testing

### Framework

**Runner:** JUnit 5 (Jupiter) via Spring Boot
**Assertions:** AssertJ fluent API
**Mocking:** Mockito 5.21.0 + Byte Buddy 1.17.6
**Property Testing:** jqwik 1.8.2 (`kelta-platform/pom.xml`)
**Integration:** Testcontainers 1.19.3, OkHttp MockWebServer, Awaitility

### Run Commands

```bash
# Build runtime (dependency for gateway/worker)
mvn clean install -DskipTests -f kelta-platform/pom.xml -pl runtime/runtime-core,runtime/runtime-events,runtime/runtime-jsonapi,runtime/runtime-module-core,runtime/runtime-module-integration,runtime/runtime-module-schema -am -B

# Run gateway tests
mvn verify -f kelta-gateway/pom.xml -B

# Run worker tests
mvn verify -f kelta-worker/pom.xml -B
```

### Test Organization

- Unit tests: `src/test/java/io/kelta/...Test.java`
- Test resources: `src/test/resources/`
- No integration tests in kelta-gateway (skipITs = true)
- Test class naming: append `Test` to class name

### Test Structure

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

**Patterns:**
- `@DisplayName` for readable test names
- `@Nested` classes for logical grouping
- `@BeforeEach` for setup, avoid `@BeforeAll`
- Method names: `shouldXxxWhenYyy()`
- AssertJ: `assertThat(actual).isEqualTo(expected)`

### Mocking (Java)

```java
// Spring MockServerHttpRequest for gateway tests
MockServerWebExchange exchange = MockServerWebExchange.from(
    MockServerHttpRequest.get("/api/ui-pages").build());

// Immutable test data
List<String> BOOTSTRAP_PATHS = List.of("/api/ui-pages", "/api/ui-menus");
```

- MockWebServer for HTTP service mocks
- MockServerHttpRequest/Exchange for gateway filter tests
- Focus on behavior over state

## TypeScript Testing

### Framework

**Runner:** Vitest 1.3+ (`kelta-web/vitest.config.ts`, `kelta-ui/app/vitest.config.ts`)
**DOM:** jsdom environment
**Components:** React Testing Library 16.3
**HTTP Mocking:** MSW 2.12.7
**Property Testing:** fast-check 4.5.3
**E2E:** Playwright 1.50.0 (`e2e-tests/`)

### Run Commands

```bash
# kelta-web
cd kelta-web && npm install
npm run lint
npm run typecheck
npm run format:check
npm run test:coverage

# kelta-ui/app
cd kelta-ui/app && npm install
npm run lint
npm run format:check
npm run test:run
```

### Configuration

```typescript
// kelta-web/vitest.config.ts
export default defineConfig({
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    include: ['packages/**/*.{test,spec}.{ts,tsx}', 'packages/**/*.property.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      thresholds: {
        global: { branches: 80, functions: 80, lines: 80, statements: 80 }
      }
    }
  }
});
```

### Test Organization

- Co-located: `FileName.test.ts` alongside source
- Property tests: `FileName.property.test.ts`
- Coverage thresholds: 80% (branches, functions, lines, statements) for kelta-web

### Test Structure

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';

describe('ResourceClient', () => {
  let client: KeltaClient;
  let resourceClient: ResourceClient<{ id: string; name: string }>;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new KeltaClient({ baseUrl: 'https://api.example.com' });
    resourceClient = client.resource<{ id: string; name: string }>('users');
  });

  it('should list resources', async () => {
    // arrange, act, assert
  });
});
```

### Mocking (TypeScript)

```typescript
// Module mocking
vi.mock('axios', async () => {
  const actual = await vi.importActual('axios');
  return {
    ...actual,
    default: { create: vi.fn(() => createMockAxiosInstance()) }
  };
});

// Factory for mock instances
const createMockAxiosInstance = () => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(),
  defaults: { headers: { common: {} } },
  interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
});
```

### Component Testing

```typescript
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

it('should add filter when button clicked', async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  render(<FilterBuilder fields={mockFields} value={[]} onChange={onChange} />);

  await user.click(screen.getByRole('button', { name: /add/i }));
  expect(onChange).toHaveBeenCalledWith([expect.objectContaining({ field: 'name' })]);
});
```

---

*Testing analysis: 2026-03-22*
*Update when test patterns change*
