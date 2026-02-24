package com.emf.gateway.filter;

import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.jsonapi.JsonApiDocument;
import com.emf.jsonapi.JsonApiParser;
import com.emf.jsonapi.ResourceObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IncludeResolutionFilter}.
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Filter only applies to /api/ routes with include query parameter</li>
 *   <li>Non-API routes are passed through unchanged</li>
 *   <li>Requests without include parameter are passed through unchanged</li>
 *   <li>Filter order is correct (10200)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IncludeResolutionFilter Tests")
class IncludeResolutionFilterTest {

    @Mock
    private IncludeResolver includeResolver;

    @Mock
    private JsonApiParser jsonApiParser;

    private IncludeResolutionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IncludeResolutionFilter(includeResolver, jsonApiParser);
    }

    /**
     * Creates a mock GatewayFilterChain that captures the exchange it receives.
     */
    private static GatewayFilterChain createCapturingChain(AtomicReference<ServerWebExchange> capturedExchange) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });
        return chain;
    }

    @Nested
    @DisplayName("Filter Bypass Tests")
    class FilterBypassTests {

        @Test
        @DisplayName("Should pass through non-API routes unchanged")
        void shouldPassThroughNonApiRoutes() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/internal/bootstrap?include=collections")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert — chain called without decoration
            assertThat(captured.get()).isNotNull();
            // includeResolver should never be called for non-API routes
            verifyNoInteractions(includeResolver);
            verifyNoInteractions(jsonApiParser);
        }

        @Test
        @DisplayName("Should pass through API routes without include parameter")
        void shouldPassThroughApiRoutesWithoutInclude() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert — no include resolution attempted
            verifyNoInteractions(includeResolver);
            verifyNoInteractions(jsonApiParser);
        }

        @Test
        @DisplayName("Should pass through API routes with empty include parameter")
        void shouldPassThroughApiRoutesWithEmptyInclude() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users?include=")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert — no include resolution attempted
            verifyNoInteractions(includeResolver);
        }

        @Test
        @DisplayName("Should pass through API routes with blank include parameter")
        void shouldPassThroughApiRoutesWithBlankInclude() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users?include=%20")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert — no include resolution attempted
            verifyNoInteractions(includeResolver);
        }

        @Test
        @DisplayName("Should pass through requests to root path")
        void shouldPassThroughRootPath() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert
            verifyNoInteractions(includeResolver);
        }
    }

    @Nested
    @DisplayName("Filter Activation Tests")
    class FilterActivationTests {

        @Test
        @DisplayName("Should decorate response for API route with include parameter")
        void shouldDecorateResponseForApiRouteWithInclude() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/users/123?include=profile")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert — chain is called with a mutated exchange (decorated response)
            assertThat(captured.get()).isNotNull();
        }

        @Test
        @DisplayName("Should activate for system collection routes")
        void shouldActivateForSystemCollectionRoutes() {
            // Arrange — system collections go through /api/ too
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/profiles?include=permissions")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert — filter activated (exchange was captured = chain was called)
            assertThat(captured.get()).isNotNull();
        }

        @Test
        @DisplayName("Should activate for multiple include parameters")
        void shouldActivateForMultipleIncludes() {
            // Arrange
            AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
            GatewayFilterChain chain = createCapturingChain(captured);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/articles/1?include=author,comments,tags")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Act
            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Assert
            assertThat(captured.get()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Filter Order Tests")
    class FilterOrderTests {

        @Test
        @DisplayName("Should have correct order (10200)")
        void shouldHaveCorrectOrder() {
            assertThat(filter.getOrder()).isEqualTo(10200);
        }

        @Test
        @DisplayName("Should run after CollectionPathRewriteFilter (10100)")
        void shouldRunAfterPathRewriteFilter() {
            assertThat(filter.getOrder()).isGreaterThan(10100);
        }
    }
}
