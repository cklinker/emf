package io.kelta.worker.health.rule;

import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.health.HealthContext;
import io.kelta.worker.health.HealthFinding;
import io.kelta.worker.health.HealthRule;
import io.kelta.worker.health.HealthSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flags active flows whose definition has no error handling — no {@code catch} and no
 * {@code retry} on any state. Such a flow aborts on the first failed task with no recovery path.
 *
 * @since 1.0.0
 */
@Component
public class FlowWithoutErrorHandlingRule implements HealthRule {

    private static final Logger log = LoggerFactory.getLogger(FlowWithoutErrorHandlingRule.class);

    private static final String SQL =
            "SELECT id, name, definition FROM flow WHERE tenant_id = ? AND active = true";

    private final ObjectMapper objectMapper;

    public FlowWithoutErrorHandlingRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return "FLOW_WITHOUT_ERROR_HANDLING";
    }

    @Override
    public List<HealthFinding> evaluate(HealthContext context) {
        List<Map<String, Object>> rows = context.jdbcTemplate().queryForList(SQL, context.tenantId());
        List<HealthFinding> findings = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object def = row.get("definition");
            if (def == null) {
                continue;
            }
            if (!hasErrorHandling(def.toString())) {
                String name = (String) row.get("name");
                findings.add(HealthFinding.of(
                        key(), HealthSeverity.WARNING,
                        "Flow has no error handling",
                        "Flow '" + name + "' defines no catch or retry on any state; it aborts on the"
                                + " first failed task. Add a Catch or retry policy.",
                        MetadataType.FLOW, (String) row.get("id"), name));
            }
        }
        return findings;
    }

    private boolean hasErrorHandling(String definitionJson) {
        if (definitionJson.isBlank()) {
            return true; // empty/trivial flow — nothing to recover from, don't flag
        }
        try {
            return containsKey(objectMapper.readTree(definitionJson));
        } catch (Exception e) {
            log.debug("Could not parse flow definition for error-handling check: {}", e.getMessage());
            return true; // unparseable — don't flag on our parsing limitation
        }
    }

    /** True if any object node carries a non-empty {@code catch} or {@code retry} entry. */
    private boolean containsKey(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (("catch".equals(name) || "retry".equals(name)) && isNonEmpty(value)) {
                    return true;
                }
                if (containsKey(value)) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsKey(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNonEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isArray() || value.isObject()) {
            return !value.isEmpty();
        }
        return true;
    }
}
