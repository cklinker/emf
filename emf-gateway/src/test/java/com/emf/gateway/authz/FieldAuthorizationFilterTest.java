package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.gateway.jsonapi.JsonApiDocument;
import com.emf.gateway.jsonapi.JsonApiParser;
import com.emf.gateway.jsonapi.ResourceObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FieldAuthorizationFilter.
 * 
 * Tests field-level authorization filtering on JSON:API responses.
 */
@ExtendWith(MockitoExtension.class)
class FieldAuthorizationFilterTest {
    
    @Mock
    private AuthzConfigCache authzConfigCache;
    
    @Mock
    private PolicyEvaluator policyEvaluator;
    
    @Mock
    private JsonApiParser jsonApiParser;
    
    @Mock
    private IncludeResolver includeResolver;
    
    @Mock
    private GatewayFilterChain filterChain;
    
    private ObjectMapper objectMapper;
    private FieldAuthorizationFilter filter;
    private DefaultDataBufferFactory bufferFactory;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new FieldAuthorizationFilter(authzConfigCache, policyEvaluator, jsonApiParser, objectMapper, includeResolver);
        bufferFactory = new DefaultDataBufferFactory();
    }
    
    @Test
    void shouldSkipFilteringWhenNoPrincipal() {
        // Given: No principal in exchange
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").build()
        );
        
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Filter chain continues without modification
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
        verifyNoInteractions(jsonApiParser);
    }
    
    @Test
    void shouldSkipFilteringForNonJsonApiResponse() {
        // Given: Principal exists but response is not JSON:API
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        // Mock filter chain to write non-JSON response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            DataBuffer buffer = bufferFactory.wrap("plain text".getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Response passes through without parsing
        StepVerifier.create(result)
            .verifyComplete();
        
        verifyNoInteractions(jsonApiParser);
    }
    
    @Test
    void shouldRemoveFieldsWhenPrincipalLacksPermission() {
        // Given: Principal without ADMIN role
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        // Create JSON:API response with sensitive field
        ResourceObject resource = new ResourceObject("users", "1");
        resource.addAttribute("name", "John Doe");
        resource.addAttribute("email", "john@example.com"); // Requires ADMIN role
        
        JsonApiDocument document = new JsonApiDocument();
        document.addData(resource);
        
        String responseJson = """
            {
              "data": {
                "type": "users",
                "id": "1",
                "attributes": {
                  "name": "John Doe",
                  "email": "john@example.com"
                }
              }
            }
            """;
        
        // Setup authorization config with field policy for email
        FieldPolicy emailPolicy = new FieldPolicy("email", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig("users", List.of(), List.of(emailPolicy));
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(authzConfigCache.getConfig("users")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(emailPolicy, principal)).thenReturn(false);
        
        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Email field is removed from response
        StepVerifier.create(result)
            .verifyComplete();
        
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();
        
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"name\"");
        assertThat(responseBody).doesNotContain("\"email\"");
        
        verify(jsonApiParser).parse(anyString());
        verify(authzConfigCache).getConfig("users");
        verify(policyEvaluator).evaluate(emailPolicy, principal);
    }
    
    @Test
    void shouldKeepFieldsWhenPrincipalHasPermission() {
        // Given: Principal with ADMIN role
        GatewayPrincipal principal = new GatewayPrincipal("admin1", List.of("ADMIN"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        // Create JSON:API response with sensitive field
        ResourceObject resource = new ResourceObject("users", "1");
        resource.addAttribute("name", "John Doe");
        resource.addAttribute("email", "john@example.com");
        
        JsonApiDocument document = new JsonApiDocument();
        document.addData(resource);
        
        String responseJson = """
            {
              "data": {
                "type": "users",
                "id": "1",
                "attributes": {
                  "name": "John Doe",
                  "email": "john@example.com"
                }
              }
            }
            """;
        
        // Setup authorization config with field policy for email
        FieldPolicy emailPolicy = new FieldPolicy("email", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig("users", List.of(), List.of(emailPolicy));
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(authzConfigCache.getConfig("users")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(emailPolicy, principal)).thenReturn(true);
        
        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: All fields remain in response
        StepVerifier.create(result)
            .verifyComplete();
        
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();
        
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"name\"");
        assertThat(responseBody).contains("\"email\"");
        
        verify(policyEvaluator).evaluate(emailPolicy, principal);
    }
    
    @Test
    void shouldKeepFieldsWithoutPolicies() {
        // Given: Principal with USER role
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        // Create JSON:API response with fields that have no policies
        ResourceObject resource = new ResourceObject("users", "1");
        resource.addAttribute("name", "John Doe");
        resource.addAttribute("username", "johndoe");
        
        JsonApiDocument document = new JsonApiDocument();
        document.addData(resource);
        
        String responseJson = """
            {
              "data": {
                "type": "users",
                "id": "1",
                "attributes": {
                  "name": "John Doe",
                  "username": "johndoe"
                }
              }
            }
            """;
        
        // Setup authorization config with no field policies
        AuthzConfig authzConfig = new AuthzConfig("users", List.of(), List.of());
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(authzConfigCache.getConfig("users")).thenReturn(Optional.of(authzConfig));
        
        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: All fields remain (default allow behavior)
        StepVerifier.create(result)
            .verifyComplete();
        
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();
        
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"name\"");
        assertThat(responseBody).contains("\"username\"");
        
        verifyNoInteractions(policyEvaluator);
    }
    
    @Test
    void shouldFilterFieldsInIncludedResources() {
        // Given: Principal without ADMIN role
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        // Create JSON:API response with included resources
        ResourceObject dataResource = new ResourceObject("posts", "1");
        dataResource.addAttribute("title", "My Post");
        
        ResourceObject includedResource = new ResourceObject("users", "1");
        includedResource.addAttribute("name", "John Doe");
        includedResource.addAttribute("email", "john@example.com"); // Requires ADMIN role
        
        JsonApiDocument document = new JsonApiDocument();
        document.addData(dataResource);
        document.addIncluded(includedResource);
        
        String responseJson = """
            {
              "data": {
                "type": "posts",
                "id": "1",
                "attributes": {
                  "title": "My Post"
                }
              },
              "included": [{
                "type": "users",
                "id": "1",
                "attributes": {
                  "name": "John Doe",
                  "email": "john@example.com"
                }
              }]
            }
            """;
        
        // Setup authorization config with field policy for email
        FieldPolicy emailPolicy = new FieldPolicy("email", "policy-1", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig("posts", List.of(), List.of(emailPolicy));
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(authzConfigCache.getConfig("posts")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(emailPolicy, principal)).thenReturn(false);
        
        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Email field is removed from included resource
        StepVerifier.create(result)
            .verifyComplete();
        
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();
        
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"name\"");
        assertThat(responseBody).doesNotContain("\"email\"");
    }
    
    @Test
    void shouldSkipFilteringWhenNoAuthzConfig() {
        // Given: Principal exists but no authz config for collection
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        ResourceObject resource = new ResourceObject("users", "1");
        resource.addAttribute("name", "John Doe");
        
        JsonApiDocument document = new JsonApiDocument();
        document.addData(resource);
        
        String responseJson = """
            {
              "data": {
                "type": "users",
                "id": "1",
                "attributes": {
                  "name": "John Doe"
                }
              }
            }
            """;
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(authzConfigCache.getConfig("users")).thenReturn(Optional.empty());
        
        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Response passes through unchanged
        StepVerifier.create(result)
            .verifyComplete();
        
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();
        
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"name\"");
        
        verify(authzConfigCache).getConfig("users");
        verifyNoInteractions(policyEvaluator);
    }
    
    @Test
    void shouldSkipFilteringWhenResponseHasErrors() {
        // Given: Principal exists but response contains errors
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        JsonApiDocument document = new JsonApiDocument();
        document.addError(new com.emf.gateway.jsonapi.JsonApiError());
        
        String responseJson = """
            {
              "errors": [{
                "status": "404",
                "title": "Not Found"
              }]
            }
            """;
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        
        // Mock filter chain to write JSON:API error response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Error response passes through unchanged
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(jsonApiParser).parse(anyString());
        verifyNoInteractions(authzConfigCache);
        verifyNoInteractions(policyEvaluator);
    }
    
    @Test
    void shouldHandleMultipleFieldPolicies() {
        // Given: Principal with USER role but not ADMIN
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = createExchangeWithPrincipal(principal);
        
        // Create JSON:API response with multiple sensitive fields
        ResourceObject resource = new ResourceObject("users", "1");
        resource.addAttribute("name", "John Doe");
        resource.addAttribute("email", "john@example.com"); // Requires ADMIN
        resource.addAttribute("phone", "555-1234"); // Requires ADMIN
        resource.addAttribute("username", "johndoe"); // No policy
        
        JsonApiDocument document = new JsonApiDocument();
        document.addData(resource);
        
        String responseJson = """
            {
              "data": {
                "type": "users",
                "id": "1",
                "attributes": {
                  "name": "John Doe",
                  "email": "john@example.com",
                  "phone": "555-1234",
                  "username": "johndoe"
                }
              }
            }
            """;
        
        // Setup authorization config with multiple field policies
        FieldPolicy emailPolicy = new FieldPolicy("email", "policy-1", List.of("ADMIN"));
        FieldPolicy phonePolicy = new FieldPolicy("phone", "policy-2", List.of("ADMIN"));
        AuthzConfig authzConfig = new AuthzConfig("users", List.of(), List.of(emailPolicy, phonePolicy));
        
        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(authzConfigCache.getConfig("users")).thenReturn(Optional.of(authzConfig));
        when(policyEvaluator.evaluate(emailPolicy, principal)).thenReturn(false);
        when(policyEvaluator.evaluate(phonePolicy, principal)).thenReturn(false);
        
        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });
        
        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then: Both email and phone are removed, but name and username remain
        StepVerifier.create(result)
            .verifyComplete();
        
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();
        
        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"name\"");
        assertThat(responseBody).contains("\"username\"");
        assertThat(responseBody).doesNotContain("\"email\"");
        assertThat(responseBody).doesNotContain("\"phone\"");
        
        verify(policyEvaluator).evaluate(emailPolicy, principal);
        verify(policyEvaluator).evaluate(phonePolicy, principal);
    }
    
    @Test
    void shouldHaveCorrectOrder() {
        // Then: Filter should run after backend response
        assertThat(filter.getOrder()).isEqualTo(100);
    }
    
    /**
     * Helper method to create an exchange with a principal.
     */
    private ServerWebExchange createExchangeWithPrincipal(GatewayPrincipal principal) {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users").build()
        );
        exchange.getAttributes().put("gateway.principal", principal);
        return exchange;
    }
}
