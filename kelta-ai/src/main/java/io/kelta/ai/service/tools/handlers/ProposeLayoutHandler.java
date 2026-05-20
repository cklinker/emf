package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import io.kelta.ai.service.tools.ProposeToolHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ProposeLayoutHandler implements ProposeToolHandler {

    @Override
    public String name() {
        return "propose_layout";
    }

    @Override
    public String description() {
        return "Propose creating a page layout for a collection. " +
                "Use this when the user wants to create or customize how a collection's records are displayed.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "collectionName", Map.of("type", "string", "description", "Name of the collection this layout is for"),
                        "name", Map.of("type", "string", "description", "Layout name"),
                        "layoutType", Map.of("type", "string", "description", "Layout type: DETAIL, EDIT, MINI, or LIST"),
                        "sections", Map.of(
                                "type", "array",
                                "description", "Layout sections with field placements",
                                "items", Map.of("type", "object")
                        ),
                        "relatedLists", Map.of(
                                "type", "array",
                                "description", "Related list configurations",
                                "items", Map.of("type", "object")
                        )
                ),
                "required", List.of("collectionName", "name", "layoutType", "sections")
        );
    }

    @Override
    public AiProposal buildProposal(Map<String, Object> input) {
        return AiProposal.pending("layout", input);
    }
}
