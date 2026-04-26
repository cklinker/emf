package io.kelta.runtime.module.integration.handlers;

import io.kelta.runtime.credential.ResolvedCredential;
import io.kelta.runtime.flow.StateDataResolver;
import io.kelta.runtime.module.integration.api.ApiOperation;
import io.kelta.runtime.module.integration.api.ApiSpec;
import io.kelta.runtime.module.integration.mapping.PayloadMapperService;
import io.kelta.runtime.module.integration.spi.ApiSpecStore;
import io.kelta.runtime.module.integration.spi.CredentialResolverPort;
import io.kelta.runtime.module.integration.spi.IdempotencyStore;
import io.kelta.runtime.module.integration.spi.IdempotencyStore.CachedResponse;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CallApiActionHandler")
class CallApiActionHandlerTest {

    private CallApiActionHandler handler;
    private ObjectMapper objectMapper;
    private ApiSpecStore specStore;
    private CredentialResolverPort credentialResolver;
    private IdempotencyStore idempotencyStore;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        PayloadMapperService mapper = new PayloadMapperService(new StateDataResolver(objectMapper));
        specStore = mock(ApiSpecStore.class);
        credentialResolver = mock(CredentialResolverPort.class);
        idempotencyStore = mock(IdempotencyStore.class);
        restTemplate = mock(RestTemplate.class);
        handler = new CallApiActionHandler(
            objectMapper, mapper, specStore, credentialResolver, idempotencyStore, restTemplate);
    }

    @Test
    @DisplayName("Action type key is CALL_API")
    void typeKey() {
        assertEquals("CALL_API", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Raw mode: builds URL from config + state, maps headers and body")
    void rawModeExecutes() throws Exception {
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"id\":42}", HttpStatus.OK));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mode", "raw");
        config.put("url", "https://api.example.com/orders/${$.input.orderId}");
        config.put("method", "POST");
        config.put("headers", Map.of("X-Trace", "${$.input.traceId}"));
        config.put("body", Map.of("status", "submitted"));

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of("input", Map.of("orderId", "O-99", "traceId", "trc")))
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful(), "expected success but got: " + result.errorMessage());
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST),
            entityCaptor.capture(), eq(String.class));
        assertEquals("https://api.example.com/orders/O-99", urlCaptor.getValue());
        assertEquals("trc", entityCaptor.getValue().getHeaders().getFirst("X-Trace"));
        assertTrue(entityCaptor.getValue().getBody().contains("\"status\":\"submitted\""));
    }

    @Test
    @DisplayName("Operation mode: loads spec + operation, fills path params, builds URL")
    void operationModeBuildsUrl() throws Exception {
        ApiSpec spec = spec("petstore", "https://petstore.swagger.io/v2");
        ApiOperation operation = operation(spec.id(), "getPetById", "GET", "/pet/{petId}");
        when(specStore.findSpec("t-1", "petstore")).thenReturn(Optional.of(spec));
        when(specStore.findOperation("t-1", "petstore", "getPetById"))
            .thenReturn(Optional.of(operation));

        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{}", HttpStatus.OK));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("mode", "operation");
        config.put("specId", "petstore");
        config.put("operationId", "getPetById");
        config.put("pathParams", Map.of("petId", "${$.queryResult.id}"));

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of("queryResult", Map.of("id", "501")))
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET),
            any(HttpEntity.class), eq(String.class));
        assertEquals("https://petstore.swagger.io/v2/pet/501", urlCaptor.getValue());
    }

    @Test
    @DisplayName("Applies api_key credential as a header")
    void appliesApiKeyHeader() throws Exception {
        when(credentialResolver.resolve(eq("t-1"), eq("salesforce"), anyString()))
            .thenReturn(new ResolvedCredential("c-1", "salesforce", "api_key",
                Map.of("value", "sk_test_123"),
                Map.of("headerName", "X-API-Key", "location", "header"),
                Instant.now()));
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        Map<String, Object> config = Map.of(
            "mode", "raw",
            "url", "https://example.com/x",
            "method", "GET",
            "credentialRef", "salesforce");

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET),
            entityCaptor.capture(), eq(String.class));
        assertEquals("sk_test_123", entityCaptor.getValue().getHeaders().getFirst("X-API-Key"));
    }

    @Test
    @DisplayName("Applies bearer_token credential as Authorization header")
    void appliesBearer() throws Exception {
        when(credentialResolver.resolve(eq("t-1"), eq("github"), anyString()))
            .thenReturn(new ResolvedCredential("c-2", "github", "bearer_token",
                Map.of("token", "ghp_token"), Map.of(), Instant.now()));
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));

        Map<String, Object> config = Map.of(
            "mode", "raw",
            "url", "https://api.github.com/user",
            "credentialRef", "github");

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), captor.capture(),
            eq(String.class));
        assertEquals("Bearer ghp_token",
            captor.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("Idempotency hit replays cached response without calling upstream")
    void idempotencyHitReplays() throws Exception {
        when(idempotencyStore.lookup(eq("t-1"), anyString()))
            .thenReturn(Optional.of(new CachedResponse(201, "{\"cached\":true}", "hash")));

        Map<String, Object> config = Map.of(
            "mode", "raw",
            "url", "https://api.example.com/orders",
            "method", "POST",
            "body", Map.of("x", 1),
            "idempotency", Map.of("enabled", true, "key", "fixed-key-1"));

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertTrue(result.successful());
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class),
            any(HttpEntity.class), eq(String.class));
        assertEquals(201, result.outputData().get("statusCode"));
    }

    @Test
    @DisplayName("Idempotency miss sends upstream with header and records response on success")
    void idempotencyMissRecords() throws Exception {
        when(idempotencyStore.lookup(eq("t-1"), anyString())).thenReturn(Optional.empty());
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("{\"ok\":true}", HttpStatus.CREATED));

        Map<String, Object> config = Map.of(
            "mode", "raw",
            "url", "https://api.example.com/orders",
            "method", "POST",
            "body", Map.of("x", 1),
            "idempotency", Map.of("enabled", true, "key", "key-7", "ttlSeconds", 60));

        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of())
            .build();

        handler.execute(ctx);

        ArgumentCaptor<HttpEntity<String>> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entity.capture(),
            eq(String.class));
        assertEquals("key-7", entity.getValue().getHeaders().getFirst("Idempotency-Key"));
        verify(idempotencyStore).record(eq("t-1"), eq("key-7"), any(), any(),
            eq(201), eq("{\"ok\":true}"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("4xx maps to Api.HttpClientError")
    void httpClientErrorMaps() throws Exception {
        when(restTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
            .thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        Map<String, Object> config = Map.of("mode", "raw", "url", "https://example.com/x");
        ActionContext ctx = ActionContext.builder()
            .tenantId("t-1")
            .actionConfigJson(objectMapper.writeValueAsString(config))
            .resolvedData(Map.of())
            .build();

        ActionResult result = handler.execute(ctx);

        assertFalse(result.successful());
        assertTrue(result.errorMessage().startsWith("Api.HttpClientError:"),
            "got: " + result.errorMessage());
    }

    @Test
    @DisplayName("validate() rejects raw mode without url and operation mode without specId")
    void validateRejectsBadConfig() throws Exception {
        // raw mode without url
        String config1 = objectMapper.writeValueAsString(Map.of("mode", "raw"));
        assertThrows(IllegalArgumentException.class, () -> handler.validate(config1));

        // operation mode without specId
        String config2 = objectMapper.writeValueAsString(
            Map.of("mode", "operation", "operationId", "x"));
        assertThrows(IllegalArgumentException.class, () -> handler.validate(config2));

        // good raw
        String good = objectMapper.writeValueAsString(
            Map.of("mode", "raw", "url", "https://example.com"));
        assertDoesNotThrow(() -> handler.validate(good));
    }

    // -----------------------------------------------------------------------

    private static ApiSpec spec(String name, String baseUrl) {
        return new ApiSpec(
            name, "t-1", name, null, "3.0.3", name, "1.0", baseUrl,
            null, null, "URL", null, "{}", "json", null,
            "deadbeef", 1, true, Instant.now());
    }

    private static ApiOperation operation(String specId, String opId, String method, String path) {
        return new ApiOperation(
            opId, "t-1", specId, opId, opId, method, path,
            "summary", null, null, null, null, null, null, false);
    }

    @SuppressWarnings("unused")
    private static List<String> _refs() {
        return List.of("ApiSpec");   // keep imports stable
    }
}
