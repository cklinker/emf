package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProposePicklistHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_picklist";
    }

    @Override
    public String description() {
        return "Propose creating a new global picklist that can be reused across collections. " +
                "Call list_picklists first to avoid duplicating an existing one.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Picklist name (lowercase, alphanumeric, underscores)"),
                        "description", Map.of("type", "string"),
                        "restricted", Map.of("type", "boolean", "description", "If true, only listed values are allowed"),
                        "values", Map.of(
                                "type", "array",
                                "description", "Picklist values",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "value", Map.of("type", "string"),
                                                "label", Map.of("type", "string"),
                                                "sortOrder", Map.of("type", "integer")
                                        )
                                )
                        )
                ),
                "required", List.of("name", "values")
        );
    }

    @Override
    public AiProposal buildProposal(Map<String, Object> input) {
        return AiProposal.pending("picklist", input);
    }
}
