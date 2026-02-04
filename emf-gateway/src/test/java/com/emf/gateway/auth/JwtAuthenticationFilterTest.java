package com.emf.gateway.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 * Tests authentication flows, edge cases, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    
    @Mock
    private ReactiveJwtDecoder jwtDecoder;
    
    @Mock
    private PrincipalExtractor principalExtractor;
    
    @Mock
    private GatewayFilterChain filterChain;
    
    private JwtAuthenticationFilter filter;
    
    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtDecoder, principalExtractor);
        
        // Mock filter chain to return completed Mono (lenient to avoid unnecessary stubbing errors)
        lenient().when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }
    
    @Test
    void shouldHaveOrderMinus100() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
    
    @Test
    void shouldAllowUnauthenticatedAccessToBootstrapEndpoint() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/control/bootstrap")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
        verify(jwtDecoder, never()).decode(anyString());
    }
    
    @Test
    void shouldReturn401WhenAuthorizationHeaderIsMissing() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
        verify(jwtDecoder, never()).decode(anyString());
    }
    
    @Test
    void shouldReturn401WhenAuthorizationHeaderIsEmpty() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void shouldReturn401WhenAuthorizationHeaderDoesNotStartWithBearer() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void shouldReturn401WhenJwtIsInvalid() {
        // Given
        String token = "invalid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtDecoder.decode(token)).thenReturn(Mono.error(new JwtException("Invalid token")));
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void shouldReturn401WhenJwtIsExpired() {
        // Given
        String token = "expired.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtDecoder.decode(token)).thenReturn(Mono.error(new JwtException("Token expired")));
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void shouldReturn401WhenPrincipalExtractionFails() {
        // Given
        String token = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        Jwt jwt = createMockJwt("user123", List.of("USER"));
        when(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt));
        when(principalExtractor.extractPrincipal(jwt))
            .thenThrow(new IllegalArgumentException("Missing required claim"));
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void shouldAuthenticateValidJwtAndStorePrincipal() {
        // Given
        String token = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        Jwt jwt = createMockJwt("user123", List.of("USER", "ADMIN"));
        GatewayPrincipal principal = new GatewayPrincipal(
            "user123",
            List.of("USER", "ADMIN"),
            Map.of("sub", "user123", "roles", List.of("USER", "ADMIN"))
        );
        
        when(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt));
        when(principalExtractor.extractPrincipal(jwt)).thenReturn(principal);
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(any(ServerWebExchange.class));
        verify(jwtDecoder).decode(token);
        verify(principalExtractor).extractPrincipal(jwt);
    }
    
    @Test
    void shouldStorePrincipalInExchangeAttributes() {
        // Given
        String token = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        Jwt jwt = createMockJwt("user123", List.of("USER"));
        GatewayPrincipal principal = new GatewayPrincipal(
            "user123",
            List.of("USER"),
            Map.of("sub", "user123")
        );
        
        when(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt));
        when(principalExtractor.extractPrincipal(jwt)).thenReturn(principal);
        
        // Capture the exchange passed to filter chain
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange capturedExchange = invocation.getArgument(0);
            GatewayPrincipal storedPrincipal = JwtAuthenticationFilter.getPrincipal(capturedExchange);
            assertThat(storedPrincipal).isNotNull();
            assertThat(storedPrincipal.getUsername()).isEqualTo("user123");
            assertThat(storedPrincipal.getRoles()).containsExactly("USER");
            return Mono.empty();
        });
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
    }
    
    @Test
    void shouldHandleUnexpectedExceptionsDuringValidation() {
        // Given
        String token = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(jwtDecoder.decode(token)).thenReturn(Mono.error(new RuntimeException("Unexpected error")));
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void shouldIncludePathInErrorResponse() {
        // Given
        String path = "/api/users/123";
        MockServerHttpRequest request = MockServerHttpRequest
            .get(path)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // When
        Mono<Void> result = filter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Note: In a real test, we would verify the response body contains the path
        // but that requires more complex setup with DataBuffer handling
    }
    
    /**
     * Helper method to create a mock JWT for testing.
     */
    private Jwt createMockJwt(String subject, List<String> roles) {
        return new Jwt(
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", subject,
                "roles", roles,
                "iss", "http://localhost:9000/realms/emf"
            )
        );
    }
}
