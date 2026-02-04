package com.emf.controlplane.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoggingFilter.
 * Verifies that correlation IDs are properly added to MDC and cleared after request completion.
 * 
 * Requirements tested:
 * - 13.1: Emit structured JSON logs with correlation IDs for all requests
 * - 13.2: Include traceId, spanId, and requestId in all log entries
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoggingFilterTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private LoggingFilter loggingFilter;

    @BeforeEach
    void setUp() {
        loggingFilter = new LoggingFilter(tracer);
        MDC.clear();
    }

    @Test
    @DisplayName("Should generate requestId when not provided in header")
    void shouldGenerateRequestIdWhenNotProvided() throws Exception {
        // Given
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn(null);
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tracer.currentSpan()).thenReturn(null);

        // Capture MDC values during filter execution
        final String[] capturedRequestId = new String[1];
        doAnswer(invocation -> {
            capturedRequestId[0] = MDC.get(LoggingFilter.REQUEST_ID_KEY);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // When
        loggingFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(capturedRequestId[0]).isNotNull();
        assertThat(capturedRequestId[0]).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        
        // Verify response headers were set
        verify(response).setHeader(eq(LoggingFilter.X_REQUEST_ID_HEADER), any());
        verify(response).setHeader(eq(LoggingFilter.X_CORRELATION_ID_HEADER), any());
    }

    @Test
    @DisplayName("Should use requestId from header when provided")
    void shouldUseRequestIdFromHeader() throws Exception {
        // Given
        String providedRequestId = "custom-request-id-123";
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn(providedRequestId);
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tracer.currentSpan()).thenReturn(null);

        // Capture MDC values during filter execution
        final String[] capturedRequestId = new String[1];
        doAnswer(invocation -> {
            capturedRequestId[0] = MDC.get(LoggingFilter.REQUEST_ID_KEY);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // When
        loggingFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(capturedRequestId[0]).isEqualTo(providedRequestId);
        verify(response).setHeader(LoggingFilter.X_REQUEST_ID_HEADER, providedRequestId);
    }

    @Test
    @DisplayName("Should use correlationId from header when provided")
    void shouldUseCorrelationIdFromHeader() throws Exception {
        // Given
        String providedCorrelationId = "correlation-id-456";
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn(null);
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(providedCorrelationId);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tracer.currentSpan()).thenReturn(null);

        // Capture MDC values during filter execution
        final String[] capturedCorrelationId = new String[1];
        doAnswer(invocation -> {
            capturedCorrelationId[0] = MDC.get(LoggingFilter.CORRELATION_ID_KEY);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // When
        loggingFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(capturedCorrelationId[0]).isEqualTo(providedCorrelationId);
        verify(response).setHeader(LoggingFilter.X_CORRELATION_ID_HEADER, providedCorrelationId);
    }

    @Test
    @DisplayName("Should populate traceId and spanId from Micrometer Tracer")
    void shouldPopulateTraceIdAndSpanIdFromTracer() throws Exception {
        // Given
        String expectedTraceId = "abc123def456";
        String expectedSpanId = "span789";
        
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn(null);
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(expectedTraceId);
        when(traceContext.spanId()).thenReturn(expectedSpanId);

        // Capture MDC values during filter execution
        final String[] capturedTraceId = new String[1];
        final String[] capturedSpanId = new String[1];
        doAnswer(invocation -> {
            capturedTraceId[0] = MDC.get(LoggingFilter.TRACE_ID_KEY);
            capturedSpanId[0] = MDC.get(LoggingFilter.SPAN_ID_KEY);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // When
        loggingFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(capturedTraceId[0]).isEqualTo(expectedTraceId);
        assertThat(capturedSpanId[0]).isEqualTo(expectedSpanId);
    }

    @Test
    @DisplayName("Should use placeholder values when no active span exists")
    void shouldUsePlaceholderValuesWhenNoActiveSpan() throws Exception {
        // Given
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn(null);
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tracer.currentSpan()).thenReturn(null);

        // Capture MDC values during filter execution
        final String[] capturedTraceId = new String[1];
        final String[] capturedSpanId = new String[1];
        doAnswer(invocation -> {
            capturedTraceId[0] = MDC.get(LoggingFilter.TRACE_ID_KEY);
            capturedSpanId[0] = MDC.get(LoggingFilter.SPAN_ID_KEY);
            return null;
        }).when(filterChain).doFilter(any(), any());

        // When
        loggingFilter.doFilterInternal(request, response, filterChain);

        // Then
        assertThat(capturedTraceId[0]).isEqualTo("no-trace");
        assertThat(capturedSpanId[0]).isEqualTo("no-span");
    }

    @Test
    @DisplayName("Should clear MDC after request completion")
    void shouldClearMdcAfterRequestCompletion() throws Exception {
        // Given
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn("test-request-id");
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tracer.currentSpan()).thenReturn(null);

        // When
        loggingFilter.doFilterInternal(request, response, filterChain);

        // Then - MDC should be cleared after filter execution
        assertThat(MDC.get(LoggingFilter.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(LoggingFilter.CORRELATION_ID_KEY)).isNull();
        assertThat(MDC.get(LoggingFilter.TRACE_ID_KEY)).isNull();
        assertThat(MDC.get(LoggingFilter.SPAN_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Should clear MDC even when exception occurs")
    void shouldClearMdcEvenWhenExceptionOccurs() throws Exception {
        // Given
        when(request.getHeader(LoggingFilter.X_REQUEST_ID_HEADER)).thenReturn("test-request-id");
        when(request.getHeader(LoggingFilter.X_CORRELATION_ID_HEADER)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(tracer.currentSpan()).thenReturn(null);
        
        doThrow(new RuntimeException("Test exception")).when(filterChain).doFilter(any(), any());

        // When/Then
        try {
            loggingFilter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException e) {
            // Expected exception
        }

        // MDC should still be cleared
        assertThat(MDC.get(LoggingFilter.REQUEST_ID_KEY)).isNull();
        assertThat(MDC.get(LoggingFilter.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("Should skip static resources")
    void shouldSkipStaticResources() {
        // Given
        when(request.getRequestURI()).thenReturn("/static/app.js");
        
        // When
        boolean shouldNotFilter = loggingFilter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should not skip API endpoints")
    void shouldNotSkipApiEndpoints() {
        // Given
        when(request.getRequestURI()).thenReturn("/api/collections");
        
        // When
        boolean shouldNotFilter = loggingFilter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isFalse();
    }

    @Test
    @DisplayName("Should skip favicon.ico")
    void shouldSkipFavicon() {
        // Given
        when(request.getRequestURI()).thenReturn("/favicon.ico");
        
        // When
        boolean shouldNotFilter = loggingFilter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }

    @Test
    @DisplayName("Should skip CSS files")
    void shouldSkipCssFiles() {
        // Given
        when(request.getRequestURI()).thenReturn("/styles/main.css");
        
        // When
        boolean shouldNotFilter = loggingFilter.shouldNotFilter(request);

        // Then
        assertThat(shouldNotFilter).isTrue();
    }
}
