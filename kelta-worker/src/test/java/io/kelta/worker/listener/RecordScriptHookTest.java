package io.kelta.worker.listener;

import io.kelta.runtime.module.integration.spi.ScriptExecutor;
import io.kelta.runtime.module.integration.spi.ScriptExecutor.ScriptExecutionResult;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RecordScriptHook} — record-event scripts executed via a mocked
 * {@link ScriptExecutor}, with the script row supplied by a mocked {@link JdbcTemplate}.
 */
class RecordScriptHookTest {

    private JdbcTemplate jdbcTemplate;
    private ScriptExecutor scriptExecutor;
    private RecordScriptHook hook;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        scriptExecutor = mock(ScriptExecutor.class);
        hook = new RecordScriptHook(jdbcTemplate, scriptExecutor);
    }

    /** Make the mocked JdbcTemplate feed one script row (of the given trigger) to the callback. */
    private void stubOneScript(String triggerType) {
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("trigger_type")).thenReturn(triggerType);
            when(rs.getString("id")).thenReturn("s1");
            when(rs.getString("script_source")).thenReturn("// script");
            when(rs.getInt("timeout_seconds")).thenReturn(5);
            h.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void noScriptsReturnsOk() {
        // JdbcTemplate default: callback never invoked -> no scripts.
        BeforeSaveResult result = hook.beforeCreate("things", Map.of("name", "A"), "t1");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.hasFieldUpdates()).isFalse();
        verifyNoInteractions(scriptExecutor);
    }

    @Test
    void beforeScriptReturningErrorBlocksTheWrite() {
        stubOneScript("BEFORE_UPDATE");
        when(scriptExecutor.execute(any())).thenReturn(ScriptExecutionResult.success(
                Map.of("result", Map.of("error", "Discount too high", "field", "discount")), 1L));

        BeforeSaveResult result = hook.beforeUpdate("things", "id1",
                Map.of("discount", 0.9), Map.of("discount", 0.1), "t1");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).field()).isEqualTo("discount");
        assertThat(result.getErrors().get(0).message()).isEqualTo("Discount too high");
    }

    @Test
    void beforeScriptReturningObjectMergesFieldUpdates() {
        stubOneScript("BEFORE_CREATE");
        when(scriptExecutor.execute(any())).thenReturn(ScriptExecutionResult.success(
                Map.of("result", Map.of("status", "NEW", "code", "X1")), 1L));

        BeforeSaveResult result = hook.beforeCreate("things", Map.of("name", "A"), "t1");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.hasFieldUpdates()).isTrue();
        assertThat(result.getFieldUpdates()).containsEntry("status", "NEW").containsEntry("code", "X1");
    }

    @Test
    void scriptRuntimeFailureBlocksTheWriteFailClosed() {
        stubOneScript("BEFORE_CREATE");
        when(scriptExecutor.execute(any())).thenReturn(ScriptExecutionResult.failure("boom", 0L));

        BeforeSaveResult result = hook.beforeCreate("things", Map.of("name", "A"), "t1");

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).message()).isEqualTo("boom");
    }

    @Test
    void afterScriptRunsAsSideEffectAndNeverThrows() {
        stubOneScript("AFTER_CREATE");
        when(scriptExecutor.execute(any())).thenThrow(new RuntimeException("kaboom"));

        // Should not propagate the exception.
        hook.afterCreate("things", Map.of("id", "id1", "name", "A"), "t1");

        verify(scriptExecutor).execute(any());
    }

    @Test
    void scriptsAreCachedWithinTheTtl() {
        stubOneScript("AFTER_CREATE");
        when(scriptExecutor.execute(any())).thenReturn(ScriptExecutionResult.success(Map.of(), 1L));

        hook.afterCreate("things", Map.of("id", "1"), "t1");
        hook.afterCreate("things", Map.of("id", "2"), "t1");

        // Two writes to the same collection => one DB load (cached), two script executions.
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        verify(scriptExecutor, times(2)).execute(any());
    }
}
