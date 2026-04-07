# kelta-ai

AI assistant service for the Kelta platform. Wraps the Anthropic Claude API for schema-aware, tenant-scoped AI interactions with SSE streaming support.

## Package Layout

```
io.kelta.ai/
  config/          ← AiConfigProperties (record), AnthropicConfig, WebConfig
  controller/      ← ChatController (SSE), ChatHistoryController, ProposalController, AiConfigController, AiUsageController
  filter/          ← TenantContextFilter, TokenLimitFilter
  model/           ← Records: Conversation, ChatMessage, AiProposal
  repository/      ← JPA repositories: ConversationRepository, ChatMessageRepository, AiConfigRepository, TokenUsageRepository
  service/         ← AnthropicService, ChatService, ProposalService, SystemPromptService, TokenTrackingService, WorkerApiClient
```

## Key Patterns

### Models
All models are Java records with static factory methods:
```java
public record Conversation(UUID id, String tenantId, String userId, String title, Instant createdAt, Instant updatedAt) {
    public static Conversation create(String tenantId, String userId, String title) { ... }
}
```

### SSE Streaming
`ChatController` returns `SseEmitter` for streaming Claude responses:
```java
@PostMapping("/stream")
public SseEmitter chatStream(@RequestHeader("X-Tenant-ID") String tenantId, ...) {
    SseEmitter emitter = new SseEmitter(config.sseTimeoutMs());
    // Non-blocking: returns immediately, streams data to client
}
```
**Reference**: `ChatController.java`

### Anthropic API Integration
- `AnthropicService` wraps `anthropic-java` SDK v2.18.0
- Model configurable via `AiConfigProperties` (default: `claude-sonnet-4-20250514`)
- Rate limit errors (429) retried with exponential backoff
- Context built via `SystemPromptService` using schema from `WorkerApiClient`

### Config Properties
```java
@ConfigurationProperties(prefix = "kelta.ai")
public record AiConfigProperties(
    AnthropicProperties anthropic,  // apiKey, defaultModel, defaultMaxTokens, defaultTemperature
    String workerServiceUrl,
    long sseTimeoutMs
) {}
```

### Error Handling
- Anthropic 429 → exponential backoff retry
- Map SDK errors to HTTP status codes
- No custom exception classes — uses standard Spring exceptions

## When Adding a New Endpoint

1. Add method to existing controller or create new `@RestController` in `controller/`
2. Accept `@RequestHeader("X-Tenant-ID")` and `@RequestHeader("X-User-Id")` for tenant isolation
3. Delegate to service in `service/`
4. Add test in `src/test/java/io/kelta/ai/controller/`

**Reference**: `ChatController.java` + `ChatControllerTest.java`

## Reference Implementations

| Pattern | File |
|---------|------|
| SSE streaming | `controller/ChatController.java` |
| REST controller | `controller/ChatHistoryController.java` |
| Anthropic wrapper | `service/AnthropicService.java` |
| Conversation mgmt | `service/ChatService.java` |
| Worker HTTP client | `service/WorkerApiClient.java` |
| Controller test | `controller/ChatControllerTest.java` |
| Service test | `service/AnthropicServiceTest.java` |

## Running Tests

```bash
mvn test                                # All tests
mvn test -Dtest=ChatControllerTest      # Single class
mvn test -Dtest=ChatControllerTest#sendsChatMessage  # Single method
mvn test -Dtest="*Service*"             # Pattern match
```

## Build Commands

```bash
make build     # mvn clean package -DskipTests
make test      # mvn test
make verify    # mvn verify
make dev       # mvn spring-boot:run (requires ANTHROPIC_API_KEY)
make lint      # mvn checkstyle:check
make format    # mvn spotless:check
```

## Test Fixtures

Use `TestFixtures.java` in `src/test/java/io/kelta/ai/` for pre-built `Conversation`, `ChatMessage`, `AiProposal`, and `AiConfigProperties` instances.
