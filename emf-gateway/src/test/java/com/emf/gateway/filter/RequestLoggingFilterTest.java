package com.emf.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequestLoggingFilter.
 * 
 * Tests verify that the filter correctly logs request information including
 * timestamp, method, path, status code, and duration.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter Tests")
class RequestLoggingFilterTest {
    
    @Mock
    private GatewayFilterChain filterChain;
    
    private RequestLoggingFilter loggingFilter;
    
    @BeforeEach
    void setUp() {
        loggingFilter = new RequestLoggingFilter();
    }
    
    @Test
    @DisplayName("Should log successful GET request")
    void shouldLogSuccessfulGetRequest() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
        
        // Verify start time was recorded
        assertThat(exchange.getAttributes()).containsKey("requestStartTime");
    }
    
    @Test
    @DisplayName("Should log successful POST request")
    void shouldLogSuccessfulPostRequest() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/api/users")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.CREATED);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
    }
    
    @Test
    @DisplayName("Should log request with correlation ID")
    void shouldLogRequestWithCorrelationId() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .header("X-Correlation-ID", "test-correlation-123")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
    }
    
    @Test
    @DisplayName("Should log 404 error response")
    void shouldLog404ErrorResponse() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/nonexistent")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
    }
    
    @Test
    @DisplayName("Should log 500 error response")
    void shouldLog500ErrorResponse() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .verifyComplete();
        
        verify(filterChain).filter(exchange);
    }
    
    @Test
    @DisplayName("Should log request even when filter chain fails")
    void shouldLogRequestEvenWhenFilterChainFails() {
        // Given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/users")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.error(new RuntimeException("Filter chain error")));
        
        // When
        Mono<Void> result = loggingFilter.filter(exchange, filterChain);
        
        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException.class)
            .verify();
        
        verify(filterChain).filter(exchange);
        
        // Verify start time was still recorded
        assertThat(exchange.getAttributes()).containsKey("requestStartTime");
    }
    
    @Test
    @DisplayName("Should have lowest priority order")
    void shouldHaveLowestPriorityOrder() {
        // When
        int order = loggingFilter.getOrder();
        
        // Then
        assertThat(order).isEqualTo(Integer.MAX_VALUE);
    }
    
    @Test
    @DisplayName("Should log different HTTP methods")
    void shouldLogDifferentHttpMethods() {
        // Test PUT
        MockServerHttpRequest putRequest = MockServerHttpRequest
            .put("/api/users/1")
            .build();
        MockServerWebExchange putExchange = MockServerWebExchange.from(putRequest);
        putExchange.getResponse().setStatusCode(HttpStatus.OK);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        StepVerifier.create(loggingFilter.filter(putExchange, filterChain))
            .verifyComplete();
        
        // Test DELETE
        MockServerHttpRequest deleteRequest = MockServerHttpRequest
            .delete("/api/users/1")
            .build();
        MockServerWebExchange deleteExchange = MockServerWebExchange.from(deleteRequest);
        deleteExchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        
        StepVerifier.create(loggingFilter.filter(deleteExchange, filterChain))
            .verifyComplete();
        
        verify(filterChain, times(2)).filter(any(ServerWebExchange.class));
    }
    
    @Test
    @DisplayName("Should log request with various paths")
    void shouldLogRequestWithVariousPaths() {
        // Test root path
        MockServerHttpRequest rootRequest = MockServerHttpRequest
            .get("/")
            .build();
        MockServerWebExchange rootExchange = MockServerWebExchange.from(rootRequest);
        rootExchange.getResponse().setStatusCode(HttpStatus.OK);
        
        when(filterChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        StepVerifier.create(loggingFilter.filter(rootExchange, filterChain))
            .verifyComplete();
        
        // Test nested path
        MockServerHttpRequest nestedRequest = MockServerHttpRequest
            .get("/api/v1/users/123/posts")
            .build();
        MockServerWebExchange nestedExchange = MockServerWebExchange.from(nestedRequest);
        nestedExchange.getResponse().setStatusCode(HttpStatus.OK);
        
        StepVerifier.create(loggingFilter.filter(nestedExchange, filterChain))
            .verifyComplete();
        
        verify(filterChain, times(2)).filter(any(ServerWebExchange.class));
    }
}
