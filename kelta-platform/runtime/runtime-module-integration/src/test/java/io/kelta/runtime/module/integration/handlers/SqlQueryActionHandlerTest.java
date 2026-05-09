package io.kelta.runtime.module.integration.handlers;

import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.workflow.ActionContext;
import io.kelta.runtime.workflow.ActionResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("SqlQueryActionHandler")
class SqlQueryActionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private SqlQueryActionHandler handler;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionTemplate = mock(TransactionTemplate.class);
        // Run the callback synchronously, no real transaction needed.
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        handler = new SqlQueryActionHandler(objectMapper, jdbcTemplate, transactionTemplate);
    }

    @Test
    @DisplayName("Should have correct action type key")
    void shouldHaveCorrectKey() {
        assertEquals("SQL_QUERY", handler.getActionTypeKey());
    }

    @Test
    @DisplayName("Should return records, rowCount, and columns for a SELECT")
    void shouldReturnRecordsForSelect() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", "a");
        row1.put("name", "Alice");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", "b");
        row2.put("name", "Bob");
        when(jdbcTemplate.queryForList("SELECT id, name FROM contact"))
                .thenReturn(List.of(row1, row2));

        String config = "{\"sql\": \"SELECT id, name FROM contact\"}";
        ActionResult result = TenantContext.callWithTenant("t1", "acme",
                () -> handler.execute(makeContext(config)));

        assertTrue(result.successful());
        assertEquals(2, result.outputData().get("rowCount"));
        assertEquals(List.of("id", "name"), result.outputData().get("columns"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) result.outputData().get("records");
        assertEquals("Alice", records.get(0).get("name"));
    }

    @Test
    @DisplayName("Should set search_path to the tenant's schema before running SELECT")
    void shouldSetSearchPathBeforeSelect() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        TenantContext.runWithTenant("t1", "acme", () ->
                handler.execute(makeContext("{\"sql\": \"SELECT 1\"}")));

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertEquals("SET LOCAL search_path = \"acme\", public", ddl.getValue());
    }

    @Test
    @DisplayName("Should escape embedded double quotes in tenant slug")
    void shouldEscapeQuotesInSlug() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        // Defence-in-depth: tenant slugs are validated upstream, but the handler
        // must double quotes anyway so a hostile slug cannot escape the identifier.
        TenantContext.runWithTenant("t1", "ev\"il", () ->
                handler.execute(makeContext("{\"sql\": \"SELECT 1\"}")));

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).execute(ddl.capture());
        assertEquals("SET LOCAL search_path = \"ev\"\"il\", public", ddl.getValue());
    }

    @Test
    @DisplayName("Should return rowsAffected for an UPDATE")
    void shouldReturnRowsAffectedForUpdate() {
        when(jdbcTemplate.update("UPDATE contact SET status = 'active'")).thenReturn(7);

        ActionResult result = TenantContext.callWithTenant("t1", "acme",
                () -> handler.execute(makeContext(
                        "{\"sql\": \"UPDATE contact SET status = 'active'\"}")));

        assertTrue(result.successful());
        assertEquals(7, result.outputData().get("rowsAffected"));
        assertEquals(true, result.outputData().get("success"));
        verify(jdbcTemplate, never()).queryForList(anyString());
    }

    @Test
    @DisplayName("Should treat INSERT...RETURNING as a result-set statement")
    void shouldHandleInsertReturning() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "x");
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row));

        ActionResult result = TenantContext.callWithTenant("t1", "acme",
                () -> handler.execute(makeContext(
                        "{\"sql\": \"INSERT INTO contact(name) VALUES('x') RETURNING id\"}")));

        assertTrue(result.successful());
        assertEquals(1, result.outputData().get("rowCount"));
    }

    @Test
    @DisplayName("Should clamp results to maxRows")
    void shouldClampToMaxRows() {
        List<Map<String, Object>> rows = List.of(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3),
                Map.of("id", 4), Map.of("id", 5));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(rows);

        ActionResult result = TenantContext.callWithTenant("t1", "acme",
                () -> handler.execute(makeContext(
                        "{\"sql\": \"SELECT id FROM contact\", \"maxRows\": 3}")));

        assertTrue(result.successful());
        assertEquals(3, result.outputData().get("rowCount"));
    }

    @Test
    @DisplayName("Should fail when sql is missing")
    void shouldFailWhenSqlMissing() {
        ActionResult result = TenantContext.callWithTenant("t1", "acme",
                () -> handler.execute(makeContext("{}")));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("sql is required"));
    }

    @Test
    @DisplayName("Should fail when tenant slug is not bound")
    void shouldFailWhenTenantNotBound() {
        ActionResult result = handler.execute(makeContext("{\"sql\": \"SELECT 1\"}"));
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("Tenant slug is not bound"));
    }

    @Test
    @DisplayName("Should surface SQL errors as failure with cause message")
    void shouldSurfaceSqlError() {
        when(jdbcTemplate.queryForList(anyString())).thenThrow(
                new BadSqlGrammarException("query", "SELECT * FROM nope",
                        new SQLException("relation \"nope\" does not exist")));

        ActionResult result = TenantContext.callWithTenant("t1", "acme",
                () -> handler.execute(makeContext("{\"sql\": \"SELECT * FROM nope\"}")));

        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("SQL error"));
        assertTrue(result.errorMessage().contains("nope"));
    }

    @Test
    @DisplayName("validate() rejects blank sql")
    void validateRejectsBlankSql() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.validate("{\"sql\": \"\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> handler.validate("{}"));
    }

    @Test
    @DisplayName("validate() rejects out-of-range maxRows")
    void validateRejectsBadMaxRows() {
        assertThrows(IllegalArgumentException.class,
                () -> handler.validate("{\"sql\": \"SELECT 1\", \"maxRows\": 0}"));
        assertThrows(IllegalArgumentException.class,
                () -> handler.validate("{\"sql\": \"SELECT 1\", \"maxRows\": 99999}"));
    }

    @Test
    @DisplayName("returnsResultSet recognises common variants")
    void returnsResultSetVariants() {
        assertTrue(SqlQueryActionHandler.returnsResultSet("SELECT 1"));
        assertTrue(SqlQueryActionHandler.returnsResultSet("  select 1"));
        assertTrue(SqlQueryActionHandler.returnsResultSet("WITH x AS (SELECT 1) SELECT * FROM x"));
        assertTrue(SqlQueryActionHandler.returnsResultSet("EXPLAIN SELECT 1"));
        assertTrue(SqlQueryActionHandler.returnsResultSet("SHOW search_path"));
        assertTrue(SqlQueryActionHandler.returnsResultSet(
                "INSERT INTO t(a) VALUES (1) RETURNING id"));
        assertTrue(SqlQueryActionHandler.returnsResultSet(
                "-- a comment\n  SELECT 1"));
        assertFalse(SqlQueryActionHandler.returnsResultSet("UPDATE t SET a=1"));
        assertFalse(SqlQueryActionHandler.returnsResultSet("DELETE FROM t"));
        assertFalse(SqlQueryActionHandler.returnsResultSet("INSERT INTO t VALUES (1)"));
    }

    private ActionContext makeContext(String configJson) {
        return ActionContext.builder()
                .tenantId("t1")
                .userId("u1")
                .actionConfigJson(configJson)
                .data(Map.of())
                .resolvedData(Map.of())
                .build();
    }
}
