package io.kelta.worker.service;

import io.kelta.worker.dependency.MetadataDependencyGraph;
import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.service.MetadataDependencyService.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetadataDependencyService")
class MetadataDependencyServiceTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MetadataDependencyService service;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static Map<String, Object> row(Object... kv) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @BeforeEach
    void setUp() {
        service = new MetadataDependencyService(jdbcTemplate, objectMapper);

        // Collections: account, contact
        stub("id, name FROM collection", List.of(
                row("id", "account", "name", "Account"),
                row("id", "contact", "name", "Contact")));

        // Fields: contact.accountId LOOKUP -> account; contact.masterAcct MASTER_DETAIL -> account;
        //         account.masterContact MASTER_DETAIL -> contact (creates a master-detail cycle)
        stub("FROM field f", List.of(
                row("id", "contact.accountId", "name", "Account", "collection_id", "contact",
                        "relationship_type", "LOOKUP", "reference_collection_id", "account"),
                row("id", "contact.masterAcct", "name", "Master Account", "collection_id", "contact",
                        "relationship_type", "MASTER_DETAIL", "reference_collection_id", "account"),
                row("id", "account.masterContact", "name", "Master Contact", "collection_id", "account",
                        "relationship_type", "MASTER_DETAIL", "reference_collection_id", "contact")));

        // Flows: orderFlow + subFlow
        stub("id, name FROM flow", List.of(
                row("id", "orderFlow", "name", "Order Flow"),
                row("id", "subFlow", "name", "Sub Flow")));

        // Flow definitions: orderFlow references account collection + invokes subFlow
        stub("definition, trigger_config FROM flow", List.of(
                row("id", "orderFlow",
                        "definition", "{\"nodes\":[{\"config\":{\"targetCollectionId\":\"account\","
                                + "\"flowId\":\"subFlow\"}}]}",
                        "trigger_config", null),
                row("id", "subFlow", "definition", "{}", "trigger_config", null)));
    }

    private void stub(String sqlFragment, List<Map<String, Object>> rows) {
        lenient().when(jdbcTemplate.queryForList(contains(sqlFragment), eq(TENANT))).thenReturn(rows);
    }

    @Test
    @DisplayName("builds nodes and edges across collections, fields, and flows")
    void buildsGraph() {
        MetadataDependencyGraph graph = service.buildGraph(TENANT);

        assertThat(graph.resolve(MetadataType.COLLECTION, "account")).isNotNull();
        assertThat(graph.resolve(MetadataType.FIELD, "contact.accountId")).isNotNull();
        assertThat(graph.resolve(MetadataType.FLOW, "orderFlow")).isNotNull();
        // Flow -> account (referenced) and flow -> subFlow (invoked) edges exist.
        assertThat(graph.directDependencies(graph.resolve(MetadataType.FLOW, "orderFlow")))
                .extracting(e -> e.to().id())
                .contains("account", "subFlow");
    }

    @Test
    @DisplayName("impact (dependents) lists what breaks if a collection changes")
    void impactDependents() {
        Map<String, Object> result = service.impact(TENANT, MetadataType.COLLECTION, "account", Direction.DEPENDENTS);

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("direction")).isEqualTo("dependents");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transitive = (List<Map<String, Object>>) result.get("transitive");
        // The lookup field and the flow that references account both depend on it.
        assertThat(transitive).extracting(m -> m.get("id"))
                .contains("contact.accountId", "orderFlow");
    }

    @Test
    @DisplayName("graph summary detects the circular master-detail between account and contact")
    void detectsMasterDetailCycle() {
        Map<String, Object> summary = service.graphSummary(TENANT);

        assertThat(summary.get("hasCycle")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> cycles = (List<List<Map<String, Object>>>) summary.get("cycles");
        assertThat(cycles).isNotEmpty();
        assertThat(cycles.get(0)).extracting(m -> m.get("id"))
                .containsExactlyInAnyOrder("account", "contact");
    }

    @Test
    @DisplayName("unknown node still returns a well-formed (empty) impact result")
    void unknownNode() {
        Map<String, Object> result = service.impact(TENANT, MetadataType.COLLECTION, "ghost", Direction.DEPENDENTS);

        assertThat(result.get("found")).isEqualTo(false);
        assertThat((List<?>) result.get("transitive")).isEmpty();
    }
}
