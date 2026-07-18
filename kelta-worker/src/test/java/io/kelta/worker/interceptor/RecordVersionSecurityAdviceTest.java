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
@DisplayName("RecordVersionSecurityAdvice")
class RecordVersionSecurityAdviceTest {

    @Mock private CerbosAuthorizationService authzService;
    @Mock private CerbosPermissionResolver permissionResolver;
    @Mock private CollectionRegistry collectionRegistry;
    @Mock private CollectionLifecycleManager lifecycleManager;
    @Mock private RecordMaskingService recordMaskingService;
    @Mock private FieldMaskingService fieldMaskingService;

    private RecordVersionSecurityAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new RecordVersionSecurityAdvice(authzService, permissionResolver, collectionRegistry,
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

    private static Map<String, Object> row(String id, Map<String, Object> snapshot,
                                           List<String> changedFields) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("collectionId", "col-1");
        attrs.put("recordId", "rec-1");
        attrs.put("versionNumber", 1);
        attrs.put("changeType", "UPDATED");
        attrs.put("snapshot", snapshot);
        attrs.put("changedFields", changedFields);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", "record-versions");
        record.put("id", id);
        record.put("attributes", attrs);
        return record;
    }

    private CollectionDefinition resolvedCollection(List<String> fieldNames, List<String> readable) {
        CollectionDefinition def = mock(CollectionDefinition.class);
        when(def.getFieldNames()).thenReturn(fieldNames);
        when(lifecycleManager.getCollectionNameById("col-1")).thenReturn("orders");
        when(collectionRegistry.get("orders")).thenReturn(def);
        when(authzService.batchCheckFieldAccess(any(), any(), any(), eq("orders"), anyList(), eq("read")))
                .thenReturn(readable);
        when(recordMaskingService.maskableConfigs(def)).thenReturn(Map.of());
        return def;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attrsOf(Object rowObj) {
        return (Map<String, Object>) ((Map<String, Object>) rowObj).get("attributes");
    }

    @Test
    @DisplayName("passes non-record-versions responses through untouched")
    void ignoresOtherPaths() {
        var req = request("/api/orders");
        Map<String, Object> body = new LinkedHashMap<>(
                Map.of("data", List.of(row("1", new LinkedHashMap<>(), List.of()))));

        Object result = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(result).isSameAs(body);
        verifyNoInteractions(authzService, collectionRegistry, lifecycleManager);
    }

    @Test
    @DisplayName("strips unreadable snapshot keys AND their names from changedFields")
    void stripsHiddenFields() {
        var req = request("/api/record-versions");
        resolvedCollection(List.of("salary", "name"), List.of("name")); // salary denied

        Map<String, Object> snapshot = new LinkedHashMap<>(
                Map.of("id", "rec-1", "salary", 100, "name", "A", "created_at", "2026-01-01"));
        List<Object> data = new ArrayList<>(List.of(row("1", snapshot, List.of("salary", "name"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        Map<String, Object> attrs = attrsOf(data.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> filtered = (Map<String, Object>) attrs.get("snapshot");
        assertThat(filtered).containsOnlyKeys("id", "name", "created_at"); // salary gone, system keys kept
        assertThat(attrs.get("changedFields")).isEqualTo(List.of("name"));
    }

    @Test
    @DisplayName("drops snapshot keys that no longer exist on the collection (fail-closed)")
    void dropsUnknownSnapshotKeys() {
        var req = request("/api/record-versions");
        resolvedCollection(List.of("name"), List.of("name"));

        Map<String, Object> snapshot = new LinkedHashMap<>(
                Map.of("name", "A", "deleted_field", "stale"));
        List<Object> data = new ArrayList<>(List.of(row("1", snapshot, List.of("name"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> filtered = (Map<String, Object>) attrsOf(data.get(0)).get("snapshot");
        assertThat(filtered).containsOnlyKeys("name");
    }

    @Test
    @DisplayName("masks MASKED-readable fields' snapshot values and stamps meta.maskedFields")
    void masksMaskedFields() {
        var req = request("/api/record-versions");
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

        Map<String, Object> snapshot = new LinkedHashMap<>(Map.of("ssn", "111-11-1111"));
        List<Object> data = new ArrayList<>(List.of(row("1", snapshot, List.of("ssn"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        Map<String, Object> rowMap = attrsOf(data.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> filtered = (Map<String, Object>) rowMap.get("snapshot");
        assertThat(filtered.get("ssn")).isEqualTo("****");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) ((Map<String, Object>) data.get(0)).get("meta");
        assertThat(meta.get("maskedFields")).isEqualTo(List.of("ssn"));
    }

    @Test
    @DisplayName("fail-closed: drops rows whose referenced collection cannot be resolved")
    void dropsUnresolvableCollection() {
        var req = request("/api/record-versions");
        when(lifecycleManager.getCollectionNameById("col-1")).thenReturn(null);

        List<Object> data = new ArrayList<>(
                List.of(row("1", new LinkedHashMap<>(Map.of("name", "A")), List.of("name"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(data).isEmpty();
    }

    @Test
    @DisplayName("fail-closed: drops rows whose snapshot is not an object")
    void dropsNonObjectSnapshot() {
        var req = request("/api/record-versions");
        resolvedCollection(List.of("name"), List.of("name"));

        Map<String, Object> record = row("1", new LinkedHashMap<>(), List.of());
        attrsOf(record).put("snapshot", "not-an-object");
        List<Object> data = new ArrayList<>(List.of(record));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);

        advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, req, null);

        assertThat(data).isEmpty();
    }
}
