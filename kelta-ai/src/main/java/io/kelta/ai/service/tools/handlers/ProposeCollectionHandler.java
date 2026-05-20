package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProposeCollectionHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_collection";
    }

    @Override
    public String description() {
        return "Propose creating a new collection (data model) with fields. " +
                "Use this when the user wants to create a new collection or entity type that does not already exist.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Collection name (lowercase, alphanumeric, underscores)"),
                        "displayName", Map.of("type", "string", "description", "Human-readable collection name"),
                        "description", Map.of("type", "string", "description", "Collection description"),
                        "displayFieldName", Map.of("type", "string", "description", "Name of the field used as record label"),
                        "fields", Map.of(
                                "type", "array",
                                "description", "Fields for the collection",
                                "items", Map.of("type", "object")
                        )
                ),
                "required", List.of("name", "fields")
        );
    }

    @Override
    public AiProposal buildProposal(Map<String, Object> input) {
        return AiProposal.pending("collection", input);
    }
}
