package io.kelta.gateway.auth;

import io.kelta.gateway.metrics.GatewayMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatAuthenticationFilter Tests")
class PatAuthenticationFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @Mock
    private GatewayMetrics metrics;

    @Mock
    private GatewayFilterChain filterChain;

    private PatAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        WebClient.Builder builder = WebClient.builder();
        filter = new PatAuthenticationFilter(redisTemplate, builder, "http://localhost", metrics);
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void sha256ShouldProduceConsistentHash() {
        String hash1 = PatAuthenticationFilter.sha256("klt_test123");
        String hash2 = PatAuthenticationFilter.sha256("klt_test123");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex chars
    }

    @Test
    void sha256ShouldProduceDifferentHashesForDifferentInputs() {
        String hash1 = PatAuthenticationFilter.sha256("klt_token1");
        String hash2 = PatAuthenticationFilter.sha256("klt_token2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("unauthorized() short-circuits cleanly when the response is already committed")
    void unauthorizedShouldNotThrowWhenResponseAlreadyCommitted() {
        // Drive into unauthorized() via the revoked-PAT branch.
        String token = "klt_committed_response_test";
        String hash = PatAuthenticationFilter.sha256(token);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("pat:revoked:" + hash)).thenReturn(Mono.just("revoked"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Pre-commit the response so headers become ReadOnlyHttpHeaders. Any
        // mutation attempt would normally throw UnsupportedOperationException
        // and surface as a 500 instead of the intended 401.
        exchange.getResponse().setComplete().block();
        assertThat(exchange.getResponse().isCommitted()).isTrue();

        // Filter must complete cleanly — no exception bubbling out.
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
    }

    @Nested
    @DisplayName("Recognised vs unknown PAT")
    class TokenRecognition {

        private static final String TOKEN = "klt_valid_token";
        private static final String HASH = PatAuthenticationFilter.sha256(TOKEN);
        private static final String PAT_JSON = """
                {"userId":"u-1","tenantId":"t-1","email":"a@b.c","scopes":"[\\"api\\"]"}""";

        private MockServerWebExchange exchangeFor(String path) {
            return MockServerWebExchange.from(MockServerHttpRequest
                    .get(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                    .build());
        }

        @Test
        @DisplayName("a cache hit authenticates and records no auth failure")
        void cacheHitDoesNotReportUnknownPat() {
            // Regression: authenticateWithPat ends in chain.filter(...), a Mono<Void> that
            // always completes empty, so a switchIfEmpty hung off it fired on every SUCCESSFUL
            // request — ~10k bogus "Unknown PAT" warnings a day, each recording an unknown_pat
            // auth failure and poisoning the metric that security alerting keys on.
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("pat:revoked:" + HASH)).thenReturn(Mono.empty());
            when(valueOps.get("pat:" + HASH)).thenReturn(Mono.just(PAT_JSON));

            MockServerWebExchange exchange = exchangeFor("/api/titles");

            StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

            verify(filterChain).filter(any(ServerWebExchange.class));
            verify(metrics, never()).recordAuthFailure(any(), eq("unknown_pat"));
            assertThat(exchange.getResponse().getStatusCode()).isNull();
            assertThat(exchange.getAttributes()).containsKey("gateway.principal");
        }

        @Test
        @DisplayName("a token in neither Redis nor the worker is rejected as unknown")
        void unknownTokenIsRejected() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get("pat:revoked:" + HASH)).thenReturn(Mono.empty());
            // Empty cache; the worker fallback also yields nothing (WebClient points at a
            // dead local port, and fetchFromWorker maps any error to empty).
            when(valueOps.get("pat:" + HASH)).thenReturn(Mono.empty());

            MockServerWebExchange exchange = exchangeFor("/api/titles");

            StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

            verify(filterChain, never()).filter(any(ServerWebExchange.class));
            verify(metrics).recordAuthFailure(any(), eq("unknown_pat"));
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Filter Order")
    class FilterOrder {
        @Test
        void shouldRunAfterJwtFilter() {
            // PatAuthenticationFilter order is -99, JwtAuthenticationFilter is -100.
            // Lower order runs first, so JWT runs before PAT.
            assertThat(filter.getOrder()).isEqualTo(-99);
        }
    }
}
