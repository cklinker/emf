package io.kelta.ai.service.tools;

import io.kelta.ai.model.AiProposal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolRegistry")
class ToolRegistryTest {

    private static class FakeReadHandler implements ReadToolHandler {
        private final String name;
        FakeReadHandler(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public String description() { return "read " + name; }
        @Override public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
        @Override public Object execute(String tenantId, String userId, Map<String, Object> input) {
            return Map.of("ok", true);
        }
    }

    private static class FakeProposeHandler implements ProposeToolHandler {
        private final String name;
        FakeProposeHandler(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public String description() { return "propose " + name; }
        @Override public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
        @Override public AiProposal buildProposal(Map<String, Object> input) {
            return AiProposal.pending("fake", input);
        }
    }

    @Test
    @DisplayName("registers handlers by name and exposes tool definitions")
    void registersHandlers() {
        ToolRegistry registry = new ToolRegistry(List.of(
                new FakeReadHandler("read_one"),
                new FakeProposeHandler("propose_one")
        ));
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.toolDefinitions()).hasSize(2);
        assertThat(registry.handler("read_one")).isPresent();
        assertThat(registry.handler("propose_one")).isPresent();
    }

    @Test
    @DisplayName("isReadTool / isProposeTool classify handlers correctly")
    void classifiesHandlers() {
        ToolRegistry registry = new ToolRegistry(List.of(
                new FakeReadHandler("get_x"),
                new FakeProposeHandler("propose_x")
        ));
        assertThat(registry.isReadTool("get_x")).isTrue();
        assertThat(registry.isProposeTool("get_x")).isFalse();
        assertThat(registry.isProposeTool("propose_x")).isTrue();
        assertThat(registry.isReadTool("propose_x")).isFalse();
        assertThat(registry.isReadTool("missing")).isFalse();
    }

    @Test
    @DisplayName("returns empty Optional for unknown tool name")
    void unknownToolReturnsEmpty() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertThat(registry.handler("anything")).isEmpty();
    }
}
