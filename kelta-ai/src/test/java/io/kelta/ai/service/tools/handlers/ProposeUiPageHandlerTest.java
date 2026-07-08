package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposeUiPageHandlerTest {

    private final ProposeUiPageHandler handler = new ProposeUiPageHandler();

    @Test
    void toolNameAndKind() {
        assertEquals("propose_ui_page", handler.name());
        assertEquals(io.kelta.ai.service.tools.ToolKind.PROPOSE, handler.kind());
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchemaRequiresNameAndComponents() {
        Map<String, Object> schema = handler.inputSchema();
        assertEquals("object", schema.get("type"));
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("name"));
        assertTrue(required.contains("components"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties.get("components"));
        assertNotNull(properties.get("variables"));
        assertNotNull(properties.get("dataSources"));
    }

    @Test
    void buildsPendingUiPageProposal() {
        Map<String, Object> input = Map.of("name", "customer_overview", "components", List.of());
        AiProposal proposal = handler.buildProposal(input);
        assertEquals("ui_page", proposal.type());
        assertEquals("pending", proposal.status());
        assertEquals(input, proposal.data());
    }
}
