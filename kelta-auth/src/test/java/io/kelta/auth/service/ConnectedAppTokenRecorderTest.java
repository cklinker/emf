package io.kelta.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectedAppTokenRecorderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ConnectedAppTokenRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ConnectedAppTokenRecorder(jdbcTemplate);
    }

    @Test
    void recordIssuedToken_insertsTokenUpdatesLastUsedAndAudits() {
        recorder.recordIssuedToken("app-1", "tenant-1", "[\"api\"]", "jti-123", Duration.ofHours(2));

        // token row: id == jti so a later revoke can match; token_hash is a sha256 (never the raw jti)
        verify(jdbcTemplate).update(
                contains("INSERT INTO connected_app_token"),
                eq("jti-123"), eq("app-1"), argThat(h -> h instanceof String s && s.length() == 64 && !s.equals("jti-123")),
                eq("[\"api\"]"), any(Timestamp.class), any(Timestamp.class));

        // last_used_at refreshed for the app
        verify(jdbcTemplate).update(
                contains("UPDATE connected_app SET last_used_at"),
                any(Timestamp.class), any(Timestamp.class), eq("app-1"));

        // audit row with TOKEN_ISSUED action embedded in the SQL, tenant + app bound
        verify(jdbcTemplate).update(
                contains("TOKEN_ISSUED"),
                anyString(), eq("tenant-1"), eq("app-1"), contains("jti-123"), any(Timestamp.class));
    }

    @Test
    void recordIssuedToken_nullScopes_defaultsToEmptyJsonArray() {
        recorder.recordIssuedToken("app-1", "tenant-1", null, "jti-9", null);

        verify(jdbcTemplate).update(
                contains("INSERT INTO connected_app_token"),
                eq("jti-9"), eq("app-1"), anyString(), eq("[]"),
                any(Timestamp.class), any(Timestamp.class));
    }

    @Test
    void recordIssuedToken_swallowsFailuresSoTokenIssuanceNeverBreaks() {
        when(jdbcTemplate.update(anyString(), (Object[]) any()))
                .thenThrow(new RuntimeException("db down"));

        // Must not propagate — issuing a valid token must not fail on bookkeeping.
        assertDoesNotThrow(() ->
                recorder.recordIssuedToken("app-1", "tenant-1", "[\"api\"]", "jti-x", Duration.ofHours(1)));
    }
}
