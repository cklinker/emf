package com.emf.worker.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RequestMetricsFilter}.
 */
class RequestMetricsFilterTest {

    private MeterRegistry meterRegistry;
    private RequestMetricsFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new RequestMetricsFilter(meterRegistry);
    }

    @Test
    void shouldRecordMetricsForCollectionGetRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);

        // Verify request counter
        Counter counter = meterRegistry.find("emf_worker_request_total")
                .tag("collection", "accounts")
                .tag("method", "GET")
                .tag("status", "200")
                .counter();
        assertNotNull(counter, "Request counter should be registered");
        assertEquals(1.0, counter.count(), "Counter should be incremented once");

        // Verify timer
        Timer timer = meterRegistry.find("emf_worker_request_duration_seconds")
                .tag("collection", "accounts")
                .tag("method", "GET")
                .timer();
        assertNotNull(timer, "Timer should be registered");
        assertEquals(1, timer.count(), "Timer should have one recording");
    }

    @Test
    void shouldRecordMetricsForCollectionPostRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/collections/contacts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Counter counter = meterRegistry.find("emf_worker_request_total")
                .tag("collection", "contacts")
                .tag("method", "POST")
                .tag("status", "201")
                .tag("status_group", "2xx")
                .counter();
        assertNotNull(counter, "Request counter should be registered for POST");
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldRecordMetricsForCollectionWithIdInPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT",
                "/api/collections/accounts/abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Counter counter = meterRegistry.find("emf_worker_request_total")
                .tag("collection", "accounts")
                .tag("method", "PUT")
                .counter();
        assertNotNull(counter, "Should extract collection name from path with ID");
        assertEquals(1.0, counter.count());
    }

    @Test
    void shouldRecordErrorMetricsFor4xxResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        // Should record error counter
        Counter errorCounter = meterRegistry.find("emf_worker_error_total")
                .tag("collection", "accounts")
                .tag("error_type", "HTTP_404")
                .counter();
        assertNotNull(errorCounter, "Error counter should be registered for 404 response");
        assertEquals(1.0, errorCounter.count());

        // Should also record in status group 4xx
        Counter requestCounter = meterRegistry.find("emf_worker_request_total")
                .tag("status_group", "4xx")
                .counter();
        assertNotNull(requestCounter);
    }

    @Test
    void shouldRecordErrorMetricsFor5xxResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Counter errorCounter = meterRegistry.find("emf_worker_error_total")
                .tag("collection", "orders")
                .tag("error_type", "HTTP_500")
                .counter();
        assertNotNull(errorCounter, "Error counter should be registered for 500 response");
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void shouldRecordErrorMetricsForException() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/collections/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("test error")).when(chain).doFilter(request, response);

        assertThrows(ServletException.class,
                () -> filter.doFilterInternal(request, response, chain));

        Counter errorCounter = meterRegistry.find("emf_worker_error_total")
                .tag("collection", "accounts")
                .tag("error_type", "ServletException")
                .counter();
        assertNotNull(errorCounter, "Error counter should be registered for exceptions");
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void shouldNotInstrumentNonCollectionRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);

        // No metrics should be recorded
        assertNull(meterRegistry.find("emf_worker_request_total").counter(),
                "Should not instrument actuator requests");
    }

    @Test
    void shouldNotInstrumentRootApiRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/other");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNull(meterRegistry.find("emf_worker_request_total").counter(),
                "Should not instrument non-collection API requests");
    }

    @Test
    void shouldTrackMultipleCollectionsSeparately() throws ServletException, IOException {
        FilterChain chain = mock(FilterChain.class);

        // Request to "accounts"
        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/collections/accounts");
        MockHttpServletResponse response1 = new MockHttpServletResponse();
        response1.setStatus(200);
        filter.doFilterInternal(request1, response1, chain);

        // Request to "contacts"
        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/api/collections/contacts");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        response2.setStatus(200);
        filter.doFilterInternal(request2, response2, chain);

        // Two separate requests to "accounts"
        MockHttpServletRequest request3 = new MockHttpServletRequest("GET", "/api/collections/accounts");
        MockHttpServletResponse response3 = new MockHttpServletResponse();
        response3.setStatus(200);
        filter.doFilterInternal(request3, response3, chain);

        Counter accountsCounter = meterRegistry.find("emf_worker_request_total")
                .tag("collection", "accounts")
                .counter();
        assertNotNull(accountsCounter);
        assertEquals(2.0, accountsCounter.count(), "accounts should have 2 requests");

        Counter contactsCounter = meterRegistry.find("emf_worker_request_total")
                .tag("collection", "contacts")
                .counter();
        assertNotNull(contactsCounter);
        assertEquals(1.0, contactsCounter.count(), "contacts should have 1 request");
    }

    @Test
    void shouldRecordDeleteRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE",
                "/api/collections/tasks/uuid-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        Counter counter = meterRegistry.find("emf_worker_request_total")
                .tag("collection", "tasks")
                .tag("method", "DELETE")
                .tag("status", "204")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }
}
