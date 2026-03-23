package io.kelta.worker.interceptor;

import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CerbosFieldSecurityAdvice Tests (Read-Side)")
class CerbosFieldSecurityAdviceTest {

    @Mock private CerbosAuthorizationService authzService;
    @Mock private CerbosPermissionResolver permissionResolver;

    private CerbosFieldSecurityAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new CerbosFieldSecurityAdvice(authzService, permissionResolver, true);
    }

    private ServletServerHttpRequest createRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-User-Email", "user@example.com");
        request.addHeader("X-User-Profile-Id", "profile-1");
        request.addHeader("X-Cerbos-Scope", "tenant-1");
        lenient().when(permissionResolver.hasIdentity(request)).thenReturn(true);
        lenient().when(permissionResolver.getEmail(request)).thenReturn("user@example.com");
        lenient().when(permissionResolver.getProfileId(request)).thenReturn("profile-1");
        lenient().when(permissionResolver.getTenantId(request)).thenReturn("tenant-1");
        return new ServletServerHttpRequest(request);
    }

    @Test
    @DisplayName("Should strip hidden fields from single record response")
    void shouldStripHiddenFieldsFromSingleRecord() {
        var request = createRequest("/api/contacts/123");
        when(authzService.batchCheckFieldAccess(any(), any(), any(), any(), anyList(), eq("read")))
                .thenReturn(List.of("name", "email"));

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", "John");
        attributes.put("email", "john@example.com");
        attributes.put("ssn", "123-45-6789"); // Should be stripped
        Map<String, Object> record = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "123", "attributes", attributes));
        Map<String, Object> body = new LinkedHashMap<>(Map.of("data", record));

        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultBody = (Map<String, Object>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> resultData = (Map<String, Object>) resultBody.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultAttrs = (Map<String, Object>) resultData.get("attributes");

        assertThat(resultAttrs).containsKeys("name", "email");
        assertThat(resultAttrs).doesNotContainKey("ssn");
    }

    @Test
    @DisplayName("Should strip hidden fields from list response")
    void shouldStripHiddenFieldsFromListResponse() {
        var request = createRequest("/api/contacts");
        when(authzService.batchCheckFieldAccess(any(), any(), any(), any(), anyList(), eq("read")))
                .thenReturn(List.of("name"));

        Map<String, Object> attrs1 = new LinkedHashMap<>();
        attrs1.put("name", "John");
        attrs1.put("secret", "hidden");
        Map<String, Object> record1 = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "1", "attributes", attrs1));

        Map<String, Object> attrs2 = new LinkedHashMap<>();
        attrs2.put("name", "Jane");
        attrs2.put("secret", "also hidden");
        Map<String, Object> record2 = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "2", "attributes", attrs2));

        Map<String, Object> body = new LinkedHashMap<>(Map.of("data", List.of(record1, record2)));

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);

        assertThat(attrs1).containsKey("name");
        assertThat(attrs1).doesNotContainKey("secret");
        assertThat(attrs2).containsKey("name");
        assertThat(attrs2).doesNotContainKey("secret");
    }

    @Test
    @DisplayName("Should not strip system fields")
    void shouldNotStripSystemFields() {
        var request = createRequest("/api/contacts/123");
        when(authzService.batchCheckFieldAccess(any(), any(), any(), any(), anyList(), eq("read")))
                .thenReturn(List.of()); // Deny everything

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("createdAt", "2026-01-01");
        attributes.put("updatedAt", "2026-01-01");
        attributes.put("createdBy", "admin");
        attributes.put("updatedBy", "admin");
        attributes.put("name", "John"); // Should be stripped
        Map<String, Object> record = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "123", "attributes", attributes));
        Map<String, Object> body = new LinkedHashMap<>(Map.of("data", record));

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);

        assertThat(attributes).containsKeys("createdAt", "updatedAt", "createdBy", "updatedBy");
        assertThat(attributes).doesNotContainKey("name");
    }

    @Test
    @DisplayName("Should skip metadata paths")
    void shouldSkipMetadataPaths() {
        var request = createRequest("/api/collections/contacts");

        Map<String, Object> body = Map.of("data", Map.of("type", "collections"));

        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);
        // Should return body unchanged
        assertThat(result).isSameAs(body);
        verify(authzService, never()).batchCheckFieldAccess(any(), any(), any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Should skip admin paths")
    void shouldSkipAdminPaths() {
        var request = createRequest("/api/admin/settings");

        Map<String, Object> body = Map.of("data", Map.of("type", "settings"));

        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);
        assertThat(result).isSameAs(body);
        verify(authzService, never()).batchCheckFieldAccess(any(), any(), any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Should do nothing when permissions disabled")
    void shouldDoNothingWhenDisabled() {
        var disabledAdvice = new CerbosFieldSecurityAdvice(authzService, permissionResolver, false);
        assertThat(disabledAdvice.supports(null, null)).isFalse();
    }

    @Test
    @DisplayName("Should handle missing attributes gracefully")
    void shouldHandleMissingAttributes() {
        var request = createRequest("/api/contacts/123");

        Map<String, Object> record = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "123"));
        // No attributes key
        Map<String, Object> body = new LinkedHashMap<>(Map.of("data", record));

        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);
        assertThat(result).isNotNull();
        verify(authzService, never()).batchCheckFieldAccess(any(), any(), any(), any(), anyList(), any());
    }

    @Test
    @DisplayName("Should make single batched Cerbos call for list")
    void shouldMakeSingleBatchedCall() {
        var request = createRequest("/api/contacts");
        when(authzService.batchCheckFieldAccess(any(), any(), any(), any(), anyList(), eq("read")))
                .thenReturn(List.of("name", "email"));

        Map<String, Object> attrs1 = new LinkedHashMap<>();
        attrs1.put("name", "John");
        attrs1.put("email", "john@example.com");
        Map<String, Object> record1 = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "1", "attributes", attrs1));

        Map<String, Object> attrs2 = new LinkedHashMap<>();
        attrs2.put("name", "Jane");
        attrs2.put("email", "jane@example.com");
        Map<String, Object> record2 = new LinkedHashMap<>(Map.of(
                "type", "contacts", "id", "2", "attributes", attrs2));

        Map<String, Object> body = new LinkedHashMap<>(Map.of("data", List.of(record1, record2)));

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, request, null);

        // Should be called once for all records (batched)
        verify(authzService, times(1)).batchCheckFieldAccess(any(), any(), any(), any(), anyList(), any());
    }
}
