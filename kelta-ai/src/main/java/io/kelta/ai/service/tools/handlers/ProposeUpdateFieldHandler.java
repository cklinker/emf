package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProposeUpdateFieldHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_update_field";
    }

    @Override
    public String description() {
        return "Propose updating properties of an existing field — displayName, required, unique, description, enumValues, validationRules. " +
                "TYPE CHANGES ARE NOT SUPPORTED. To replace a field, use propose_remove_field followed by propose_add_fields.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of("type", "string"),
                        "fieldName", Map.of("type", "string"),
                        "changes", Map.of(
                                "type", "object",
                                "description", "Only these properties may change: displayName, required, unique, description, enumValues, validationRules. " +
                                        "Do NOT include 'type' — type changes are rejected.",
                                "properties", Map.of(
                                        "displayName", Map.of("type", "string"),
                                        "required", Map.of("type", "boolean"),
                                        "unique", Map.of("type", "boolean"),
                                        "description", Map.of("type", "string"),
                                        "enumValues", Map.of("type", "array", "items", Map.of("type", "string")),
                                        "validationRules", Map.of("type", "array", "items", Map.of("type", "object"))
                                )
                        )
                ),
                "required", List.of("collectionName", "fieldName", "changes")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiProposal buildProposal(Map<String, Object> input) {
        Object changes = input.get("changes");
        if (changes instanceof Map<?, ?> m && m.containsKey("type")) {
            throw new IllegalArgumentException(
                    "Type changes are not supported by propose_update_field. " +
                            "Use propose_remove_field then propose_add_fields to replace the field.");
        }
        return AiProposal.pending("update_field", input);
    }
}
