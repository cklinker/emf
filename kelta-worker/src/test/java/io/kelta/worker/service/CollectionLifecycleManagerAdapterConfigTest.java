package io.kelta.worker.service;

import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("CollectionLifecycleManager.parseAdapterConfig (Rec 4)")
class CollectionLifecycleManagerAdapterConfigTest {

    private CollectionLifecycleManager manager;

    @BeforeEach
    void setUp() {
        // A real ObjectMapper so JSON actually parses.
        manager = new CollectionLifecycleManager(
                mock(CollectionRegistry.class),
                mock(StorageAdapter.class),
                mock(JdbcTemplate.class),
                new ObjectMapper());
    }

    @Test
    @DisplayName("parses a JSON object into a flat string map")
    void parsesJsonObject() {
        Map<String, String> result = manager.parseAdapterConfig(
                "{\"adapterType\":\"external-rest\",\"baseUrl\":\"https://api.test\"}", "orders");

        assertThat(result)
                .containsEntry("adapterType", "external-rest")
                .containsEntry("baseUrl", "https://api.test");
    }

    @Test
    @DisplayName("stringifies non-string JSON values")
    void stringifiesValues() {
        Map<String, String> result = manager.parseAdapterConfig("{\"pageSize\":50,\"cache\":true}", "orders");

        assertThat(result).containsEntry("pageSize", "50").containsEntry("cache", "true");
    }

    @Test
    @DisplayName("returns an empty map for null, blank, or empty-object input")
    void emptyForNoConfig() {
        assertThat(manager.parseAdapterConfig(null, "orders")).isEmpty();
        assertThat(manager.parseAdapterConfig("", "orders")).isEmpty();
        assertThat(manager.parseAdapterConfig("   ", "orders")).isEmpty();
        assertThat(manager.parseAdapterConfig("{}", "orders")).isEmpty();
    }

    @Test
    @DisplayName("degrades to an empty map on malformed JSON (load never fails)")
    void emptyOnMalformed() {
        assertThat(manager.parseAdapterConfig("{not valid json", "orders")).isEmpty();
    }
}
