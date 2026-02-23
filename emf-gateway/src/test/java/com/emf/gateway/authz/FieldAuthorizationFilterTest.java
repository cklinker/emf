package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.jsonapi.JsonApiDocument;
import com.emf.jsonapi.JsonApiError;
import com.emf.jsonapi.JsonApiParser;
import com.emf.jsonapi.ResourceObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FieldAuthorizationFilter.
 *
 * Tests include parameter processing on JSON:API responses:
 * - Skipping when no principal
 * - Skipping when no include parameter
 * - Processing include parameters via IncludeResolver
 * - Handling error responses
 */
@ExtendWith(MockitoExtension.class)
class FieldAuthorizationFilterTest {

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
        filter = new FieldAuthorizationFilter(jsonApiParser, objectMapper, includeResolver);
        bufferFactory = new DefaultDataBufferFactory();
    }

    @Test
    void shouldHaveCorrectOrder() {
        // Filter should run before NettyWriteResponseFilter (order -1)
        // to intercept and modify the response body
        assertThat(filter.getOrder()).isEqualTo(-2);
    }

    @Test
    void shouldSkipWhenNoPrincipal() {
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
        verifyNoInteractions(includeResolver);
    }

    @Test
    void shouldSkipWhenNoIncludeParameter() {
        // Given: Principal exists but no include query parameter
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build()
        );
        exchange.getAttributes().put("gateway.principal", principal);

        when(filterChain.filter(any())).thenReturn(Mono.empty());

        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then: Filter chain continues without response decoration
        StepVerifier.create(result)
                .verifyComplete();

        verify(filterChain).filter(any());
        verifyNoInteractions(jsonApiParser);
        verifyNoInteractions(includeResolver);
    }

    @Test
    void shouldProcessIncludeParameters() {
        // Given: Principal exists and include parameter is present
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/posts?include=author").build()
        );
        exchange.getAttributes().put("gateway.principal", principal);

        // Create JSON:API response
        ResourceObject dataResource = new ResourceObject("posts", "1");
        dataResource.addAttribute("title", "My Post");

        ResourceObject includedResource = new ResourceObject("users", "1");
        includedResource.addAttribute("name", "John Doe");

        JsonApiDocument document = new JsonApiDocument();
        document.addData(dataResource);
        document.setSingleResource(true);

        String responseJson = """
                {
                  "data": {
                    "type": "posts",
                    "id": "1",
                    "attributes": {
                      "title": "My Post"
                    }
                  }
                }
                """;

        when(jsonApiParser.parse(anyString())).thenReturn(document);
        when(includeResolver.resolveIncludes(anyList(), anyList()))
                .thenReturn(Mono.just(List.of(includedResource)));

        // Mock filter chain to write JSON:API response
        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange ex = invocation.getArgument(0);
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = bufferFactory.wrap(responseJson.getBytes());
            return ex.getResponse().writeWith(Mono.just(buffer));
        });

        // When: Filter is applied
        Mono<Void> result = filter.filter(exchange, filterChain);

        // Then: Include resolver is called and resources are included in response
        StepVerifier.create(result)
                .verifyComplete();

        verify(jsonApiParser).parse(anyString());
        verify(includeResolver).resolveIncludes(anyList(), anyList());

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();

        assertThat(responseBody).isNotNull();
        assertThat(responseBody).contains("\"included\"");
        assertThat(responseBody).contains("\"name\"");
    }

    @Test
    void shouldHandleErrorResponse() {
        // Given: Principal exists and include parameter is present, but response has errors
        GatewayPrincipal principal = new GatewayPrincipal("user1", List.of("USER"), Map.of());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users?include=posts").build()
        );
        exchange.getAttributes().put("gateway.principal", principal);

        JsonApiDocument document = new JsonApiDocument();
        document.addError(new JsonApiError());

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

        // Then: Error response passes through unchanged, no include resolution
        StepVerifier.create(result)
                .verifyComplete();

        verify(jsonApiParser).parse(anyString());
        verifyNoInteractions(includeResolver);
    }
}
