package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.StorageConfig;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.CollectionVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CollectionVersionService")
class CollectionVersionServiceTest {

    private CollectionVersionRepository versionRepository;
    private CollectionRegistry collectionRegistry;
    private JdbcTemplate jdbcTemplate;
    private CollectionVersionService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        versionRepository = mock(CollectionVersionRepository.class);
        collectionRegistry = mock(CollectionRegistry.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new CollectionVersionService(versionRepository, collectionRegistry, jdbcTemplate, objectMapper);
    }

    /** Live "orders" collection with fields {name:STRING, amount:INTEGER, note:STRING}. */
    private CollectionDefinition ordersDef() {
        return CollectionDefinition.builder()
                .name("orders")
                .storageConfig(StorageConfig.physicalTable("orders_tbl"))
                .addField(FieldDefinition.string("name"))
                .addField(FieldDefinition.integer("amount"))
                .addField(FieldDefinition.string("note"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void stubResolution() {
        // id → name
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("c1"))).thenReturn("orders");
        when(collectionRegistry.get("orders")).thenReturn(ordersDef());
        // record count
        when(jdbcTemplate.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class))).thenReturn(42L);
        when(versionRepository.nextVersion("c1")).thenReturn(3);
    }

    @Test
    @DisplayName("plan diffs current vs target: ADD, REMOVE, MODIFY with risks")
    void planDiff() {
        stubResolution();
        // Target v2: {name:STRING, amount:STRING(changed), extra:STRING(added)} — 'note' removed.
        String targetJson = "[{\"name\":\"name\",\"type\":\"STRING\",\"nullable\":true},"
                + "{\"name\":\"amount\",\"type\":\"STRING\",\"nullable\":true},"
                + "{\"name\":\"extra\",\"type\":\"STRING\",\"nullable\":true}]";
        when(versionRepository.findSchema("c1", 2)).thenReturn(Optional.of(targetJson));

        Optional<Map<String, Object>> planOpt = service.buildPlan("c1", 2);

        assertThat(planOpt).isPresent();
        Map<String, Object> plan = planOpt.get();
        assertThat(plan).containsEntry("collectionName", "orders")
                .containsEntry("toVersion", 2)
                .containsEntry("estimatedRecordsAffected", 42L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("steps");
        assertThat(steps).extracting(s -> s.get("operation"))
                .containsExactlyInAnyOrder("ADD_FIELD", "REMOVE_FIELD", "MODIFY_FIELD");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> risks = (List<Map<String, Object>>) plan.get("risks");
        // REMOVE (data loss) + MODIFY (incompatible) are high-risk; ADD is not.
        assertThat(risks).hasSize(2);
        assertThat(risks).allMatch(r -> "high".equals(r.get("level")));
    }

    @Test
    @DisplayName("plan returns empty when the target version does not exist")
    void planMissingVersion() {
        stubResolution();
        when(versionRepository.findSchema("c1", 9)).thenReturn(Optional.empty());

        assertThat(service.buildPlan("c1", 9)).isEmpty();
    }

    @Test
    @DisplayName("snapshot writes the current schema as the next version")
    void snapshot() {
        stubResolution();

        int version = service.snapshot("c1");

        assertThat(version).isEqualTo(3);
        verify(versionRepository).insertSnapshot(eq("c1"), eq(3), contains("\"name\":\"name\""));
    }
}
