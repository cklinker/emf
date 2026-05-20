package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProposeRemoveFieldHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_remove_field";
    }

    @Override
    public String description() {
        return "Propose removing a field from a collection. DESTRUCTIVE — surface this clearly to the user. " +
                "Always include a short reason explaining why.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of("type", "string"),
                        "fieldName", Map.of("type", "string"),
                        "reason", Map.of("type", "string", "description", "Why this field should be removed")
                ),
                "required", List.of("collectionName", "fieldName", "reason")
        );
    }

    @Override
    public AiProposal buildProposal(Map<String, Object> input) {
        return AiProposal.pending("remove_field", input);
    }
}
