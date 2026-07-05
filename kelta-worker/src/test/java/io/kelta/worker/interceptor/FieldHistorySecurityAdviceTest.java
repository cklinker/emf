package io.kelta.worker.interceptor;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.CollectionLifecycleManager;
import io.kelta.worker.service.FieldMaskingService;
import io.kelta.worker.service.FieldMaskingService.MaskType;
import io.kelta.worker.service.FieldMaskingService.MaskingConfig;
import io.kelta.worker.service.RecordMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FieldHistorySecurityAdvice")
class FieldHistorySecurityAdviceTest {

    @Mock private CerbosAuthorizationService authzService;
    @Mock private CerbosPermissionResolver permissionResolver;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private CollectionLifecycleManager lifecycleManager;
    @Mock private RecordMaskingService recordMaskingService;
    @Mock private FieldMaskingService fieldMaskingService;

    private FieldHistorySecurityAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new FieldHistorySecurityAdvice(authzService, permissionResolver, collectionRegistry,
                lifecycleManager, recordMaskingService, fieldMaskingService, true);
    }

    private ServletServerHttpRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        lenient().when(permissionResolver.hasIdentity(request)).thenReturn(true);
        lenient().when(permissionResolver.getEmail(request)).thenReturn("user@example.com");
        lenient().when(permissionResolver.getProfileId(request)).thenReturn("profile-1");
        lenient().when(permissionResolver.getTenantId(request)).thenReturn("tenant-1");
        return new ServletServerHttpRequest(request);
    }

    private static Map<String, Object> row(String id, String fieldName, Object oldV, Object newV) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("collectionId", "col-1");
        attrs.put("fieldName", fieldName);
        attrs.put("oldValue", oldV);
        attrs.put("newValue", newV);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", "field-history");
        record.put("id", id);
        record.put("attributes", attrs);
        return record;
    }

    @Test
    @DisplayName("passes non-field-history responses through untouched")
    void ignoresOtherPaths() {
        var req = request("/api/orders");
        Map<String, Object> body = new LinkedHashMap<>(Map.of("data", List.of(row("1", "name", "a", "b"))));

        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(result).isSameAs(body);
        verifyNoInteractions(authzService, collectionRegistry, lifecycleManager);
    }

    @Test
    @DisplayName("drops history rows whose referenced field the caller cannot read")
    void dropsDeniedFieldRows() {
        var req = request("/api/field-history");
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(def.getFieldNames()).thenReturn(List.of("salary", "name"));
        when(lifecycleManager.getCollectionNameById("col-1")).thenReturn("orders");
        when(collectionRegistry.get("orders")).thenReturn(def);
        when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("orders"), anyList(), eq("read")))
                .thenReturn(List.of("name")); // salary denied
        when(recordMaskingService.maskableConfigs(def)).thenReturn(Map.of());

        List<Object> data = new ArrayList<>(List.of(
                row("1", "salary", 100, 200),
                row("2", "name", "A", "B")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(data).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> kept = (Map<String, Object>) data.get(0);
        assertThat(kept.get("id")).isEqualTo("2");
    }

    @Test
    @DisplayName("redacts old/new values for a masked referenced field")
    void redactsMaskedFieldValues() {
        var req = request("/api/field-history");
        CollectionDefinition def = mock(CollectionDefinition.class);
        MaskingConfig config = new MaskingConfig(MaskType.FULL, '*', null);
        when(def.getFieldNames()).thenReturn(List.of("ssn"));
        when(lifecycleManager.getCollectionNameById("col-1")).thenReturn("orders");
        when(collectionRegistry.get("orders")).thenReturn(def);
        when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("orders"), anyList(), eq("read")))
                .thenReturn(List.of("ssn"));
        when(recordMaskingService.maskableConfigs(def)).thenReturn(Map.of("ssn", config));
        when(recordMaskingService.maskedFieldsFor(any(), any(), any(), eq("orders"), any()))
                .thenReturn(Set.of("ssn"));
        when(fieldMaskingService.mask(any(), eq(config))).thenReturn("****");

        List<Object> data = new ArrayList<>(List.of(row("1", "ssn", "111-11-1111", "222-22-2222")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(data).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) data.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) record.get("attributes");
        assertThat(attrs.get("oldValue")).isEqualTo("****");
        assertThat(attrs.get("newValue")).isEqualTo("****");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) record.get("meta");
        assertThat(meta.get("maskedFields")).isEqualTo(List.of("ssn"));
    }

    @Test
    @DisplayName("fail-closed: drops rows whose referenced collection cannot be resolved")
    void dropsUnresolvableCollection() {
        var req = request("/api/field-history");
        when(lifecycleManager.getCollectionNameById("col-1")).thenReturn(null);

        List<Object> data = new ArrayList<>(List.of(row("1", "name", "a", "b")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(data).isEmpty();
    }
}
