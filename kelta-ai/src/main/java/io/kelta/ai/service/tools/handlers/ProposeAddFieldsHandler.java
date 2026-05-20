package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProposeAddFieldsHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_add_fields";
    }

    @Override
    public String description() {
        return "Propose adding one or more new fields to an existing collection. " +
                "Use this when the user wants to extend a collection — do NOT recreate the whole collection. " +
                "Call get_collection_schema first to confirm which fields are missing.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of("type", "string", "description", "Existing collection name"),
                        "fields", Map.of(
                                "type", "array",
                                "description", "Fields to add — same schema as propose_collection.fields[]",
                                "items", Map.of("type", "object")
                        )
                ),
                "required", List.of("collectionName", "fields")
        );
    }

    @Override
    public AiProposal buildProposal(Map<String, Object> input) {
        return AiProposal.pending("add_fields", input);
    }
}
