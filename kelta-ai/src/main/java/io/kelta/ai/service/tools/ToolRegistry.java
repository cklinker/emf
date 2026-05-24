package io.kelta.ai.service.tools;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers;

    public ToolRegistry(List<ToolHandler> handlers) {
        Map<String, ToolHandler> byName = new HashMap<>();
        for (ToolHandler h : handlers) {
            byName.put(h.name(), h);
        }
        this.handlers = Map.copyOf(byName);
    }

    public Optional<ToolHandler> handler(String name) {
        return Optional.ofNullable(handlers.get(name));
    }

    public boolean isReadTool(String name) {
        return handler(name).map(h -> h.kind() == ToolKind.READ).orElse(false);
    }

    public boolean isProposeTool(String name) {
        return handler(name).map(h -> h.kind() == ToolKind.PROPOSE).orElse(false);
    }

    public List<ToolUnion> toolDefinitions() {
        return handlers.values().stream()
                .map(this::toToolUnion)
                .toList();
    }

    public int size() {
        return handlers.size();
    }

    private ToolUnion toToolUnion(ToolHandler handler) {
        Tool tool = Tool.builder()
                .name(handler.name())
                .description(handler.description())
                .inputSchema(buildInputSchema(handler.inputSchema()))
                .build();
        return ToolUnion.ofTool(tool);
    }

    private Tool.InputSchema buildInputSchema(Map<String, Object> rawSchema) {
        Tool.InputSchema.Properties.Builder propertiesBuilder = Tool.InputSchema.Properties.builder();
        Object propertiesObj = rawSchema.get("properties");
        if (propertiesObj instanceof Map<?, ?> propertiesMap) {
            for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
                propertiesBuilder.putAdditionalProperty(
                        String.valueOf(entry.getKey()),
                        JsonValue.from(entry.getValue()));
            }
        }

        Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder()
                .properties(propertiesBuilder.build());

        Object requiredObj = rawSchema.get("required");
        if (requiredObj instanceof List<?> requiredList && !requiredList.isEmpty()) {
            List<String> required = new ArrayList<>(requiredList.size());
            for (Object item : requiredList) {
                required.add(String.valueOf(item));
            }
            schemaBuilder.required(required);
        }

        return schemaBuilder.build();
    }
}
