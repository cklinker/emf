package io.kelta.mcp.auth;

import io.kelta.mcp.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatSessionStoreTest {

    private final PatSessionStore store = new PatSessionStore(new McpProperties("http://gw", 30, 60_000, null));

    @Test
    void putsAndRetrievesPat() {
        store.put("session-1", "klt_abc");
        assertThat(store.touchAndGet("session-1")).isEqualTo("klt_abc");
    }

    @Test
    void touchAndGetReturnsNullForUnknownSession() {
        assertThat(store.touchAndGet("unknown")).isNull();
    }

    @Test
    void removeDropsTheEntry() {
        store.put("s2", "klt_xyz");
        store.remove("s2");
        assertThat(store.touchAndGet("s2")).isNull();
        assertThat(store.size()).isZero();
    }
}
