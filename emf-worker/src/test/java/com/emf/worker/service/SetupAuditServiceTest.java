package com.emf.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SetupAuditService}.
 */
class SetupAuditServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SetupAuditService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new SetupAuditService(jdbcTemplate);
    }

    @Test
    void shouldInsertAuditEntryWithAllFields() {
        service.log("tenant-1", "user-1", "CREATE", "Schema", "collection",
                "coll-123", "accounts", null, "{\"name\":\"accounts\"}");

        verify(jdbcTemplate).update(
                contains("INSERT INTO setup_audit_trail"),
                any(String.class),  // id
                eq("tenant-1"),     // tenant_id
                eq("user-1"),       // user_id
                eq("CREATE"),       // action
                eq("Schema"),       // section
                eq("collection"),   // entity_type
                eq("coll-123"),     // entity_id
                eq("accounts"),     // entity_name
                isNull(),           // old_value
                eq("{\"name\":\"accounts\"}"),  // new_value
                any(), any(), any()  // timestamp, created_at, updated_at
        );
    }

    @Test
    void shouldHandleNullOldAndNewValues() {
        service.log("tenant-1", "user-1", "DELETE", "Schema", "collection",
                "coll-123", "accounts", null, null);

        verify(jdbcTemplate).update(
                contains("INSERT INTO setup_audit_trail"),
                any(String.class), eq("tenant-1"), eq("user-1"),
                eq("DELETE"), eq("Schema"), eq("collection"),
                eq("coll-123"), eq("accounts"),
                isNull(), isNull(),
                any(), any(), any()
        );
    }

    @Test
    void shouldUseSystemAsFallbackForNullUserId() {
        service.log("tenant-1", null, "CREATE", "Schema", "collection",
                "coll-123", "accounts", null, null);

        verify(jdbcTemplate).update(
                contains("INSERT INTO setup_audit_trail"),
                any(String.class), eq("tenant-1"), eq("system"),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void shouldCatchDatabaseExceptionWithoutThrowing() {
        doThrow(new RuntimeException("Connection refused"))
                .when(jdbcTemplate).update(anyString(),
                        any(), any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any(), any());

        // Should NOT throw
        service.log("tenant-1", "user-1", "CREATE", "Schema", "collection",
                "coll-123", "accounts", null, null);

        verify(jdbcTemplate).update(anyString(),
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }
}
