package com.emf.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SetupAuditWriteListener}.
 */
class SetupAuditWriteListenerTest {

    private SetupAuditService auditService;
    private SetupAuditWriteListener listener;

    @BeforeEach
    void setUp() {
        auditService = mock(SetupAuditService.class);
        listener = new SetupAuditWriteListener(auditService);
    }

    @Test
    void shouldAuditCreateOnSetupCollection() {
        Map<String, Object> data = Map.of("name", "accounts", "apiName", "accounts");

        listener.afterCreate("collections", "tenant-1", "user-1", "coll-123", data);

        verify(auditService).log(
                eq("tenant-1"), eq("user-1"), eq("CREATE"), eq("Schema"),
                eq("collection"), eq("coll-123"), eq("accounts"),
                isNull(), contains("accounts"));
    }

    @Test
    void shouldAuditUpdateOnSetupCollection() {
        Map<String, Object> data = Map.of("label", "Updated Label");

        listener.afterUpdate("fields", "tenant-1", "user-1", "field-456", data);

        verify(auditService).log(
                eq("tenant-1"), eq("user-1"), eq("UPDATE"), eq("Schema"),
                eq("field"), eq("field-456"), isNull(),
                isNull(), contains("Updated Label"));
    }

    @Test
    void shouldAuditDeleteOnSetupCollection() {
        listener.afterDelete("profiles", "tenant-1", "user-1", "prof-789");

        verify(auditService).log(
                eq("tenant-1"), eq("user-1"), eq("DELETE"), eq("Profiles"),
                eq("profile"), eq("prof-789"), isNull(),
                isNull(), isNull());
    }

    @Test
    void shouldSkipNonSetupCollection() {
        listener.afterCreate("my-custom-collection", "tenant-1", "user-1", "rec-1",
                Map.of("name", "test"));

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldSkipNonSetupCollectionOnUpdate() {
        listener.afterUpdate("orders", "tenant-1", "user-1", "order-1",
                Map.of("status", "SHIPPED"));

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldSkipNonSetupCollectionOnDelete() {
        listener.afterDelete("invoices", "tenant-1", "user-1", "inv-1");

        verifyNoInteractions(auditService);
    }

    @Test
    void shouldMapProfilePermissionCollections() {
        listener.afterCreate("profile-system-permissions", "t1", "u1", "psp-1",
                Map.of("name", "VIEW_ALL"));

        verify(auditService).log(eq("t1"), eq("u1"), eq("CREATE"), eq("Profiles"),
                eq("system-permissions"), eq("psp-1"), eq("VIEW_ALL"),
                isNull(), any());
    }

    @Test
    void shouldMapPermissionSetCollections() {
        listener.afterCreate("permission-sets", "t1", "u1", "ps-1",
                Map.of("name", "Sales Admin"));

        verify(auditService).log(eq("t1"), eq("u1"), eq("CREATE"), eq("Permission Sets"),
                eq("permission-set"), eq("ps-1"), eq("Sales Admin"),
                isNull(), any());
    }

    @Test
    void shouldMapUsersCollection() {
        listener.afterUpdate("users", "t1", "u1", "u2", Map.of("email", "new@example.com"));

        verify(auditService).log(eq("t1"), eq("u1"), eq("UPDATE"), eq("Users"),
                eq("user"), eq("u2"), isNull(),
                isNull(), any());
    }

    @Test
    void shouldMapFlowsCollection() {
        listener.afterCreate("flows", "t1", "u1", "flow-1", Map.of("name", "My Flow"));

        verify(auditService).log(eq("t1"), eq("u1"), eq("CREATE"), eq("Flows"),
                eq("flow"), eq("flow-1"), eq("My Flow"),
                isNull(), any());
    }

    @Test
    void shouldMapOidcProviders() {
        listener.afterDelete("oidc-providers", "t1", "u1", "oidc-1");

        verify(auditService).log(eq("t1"), eq("u1"), eq("DELETE"), eq("OIDC"),
                eq("oidc-provider"), eq("oidc-1"), isNull(),
                isNull(), isNull());
    }

    @Test
    void shouldResolveEntityTypeForJunctionTable() {
        assertEquals("system-permissions",
                SetupAuditWriteListener.resolveEntityType("profile-system-permissions"));
        assertEquals("object-permissions",
                SetupAuditWriteListener.resolveEntityType("permset-object-permissions"));
        assertEquals("permission-sets",
                SetupAuditWriteListener.resolveEntityType("user-permission-sets"));
    }

    @Test
    void shouldResolveEntityTypeForRegularCollection() {
        assertEquals("collection", SetupAuditWriteListener.resolveEntityType("collections"));
        assertEquals("field", SetupAuditWriteListener.resolveEntityType("fields"));
        assertEquals("profile", SetupAuditWriteListener.resolveEntityType("profiles"));
        assertEquals("user", SetupAuditWriteListener.resolveEntityType("users"));
        assertEquals("flow", SetupAuditWriteListener.resolveEntityType("flows"));
    }

    @Test
    void shouldExtractEntityNameFromNameField() {
        Map<String, Object> data = Map.of("name", "Test Entity", "id", "123");

        listener.afterCreate("collections", "t1", "u1", "c1", data);

        verify(auditService).log(any(), any(), any(), any(), any(),
                any(), eq("Test Entity"), any(), any());
    }

    @Test
    void shouldExtractEntityNameFromLabelField() {
        Map<String, Object> data = Map.of("label", "My Label", "id", "123");

        listener.afterCreate("fields", "t1", "u1", "f1", data);

        verify(auditService).log(any(), any(), any(), any(), any(),
                any(), eq("My Label"), any(), any());
    }

    @Test
    void shouldExtractEntityNameFromEmailField() {
        Map<String, Object> data = Map.of("email", "alice@example.com", "id", "123");

        listener.afterCreate("users", "t1", "u1", "u2", data);

        verify(auditService).log(any(), any(), any(), any(), any(),
                any(), eq("alice@example.com"), any(), any());
    }
}
