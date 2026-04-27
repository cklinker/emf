package io.kelta.mcp.observe;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import io.kelta.mcp.auth.RequestPatHolder;
import io.kelta.mcp.auth.RequestSlugHolder;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatPropagatingToolDecoratorTest {

    private final PatPropagatingToolDecorator decorator = new PatPropagatingToolDecorator();

    private SyncToolSpecification stub(java.util.function.BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
        Tool tool = Tool.builder()
                .name("stub")
                .description("test")
                .inputSchema(new JsonSchema("object", Map.of(), List.of(), false, null, null))
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(handler::apply)
                .build();
    }

    private McpSyncServerExchange exchangeWithContext(McpTransportContext ctx) {
        McpAsyncServerExchange asyncExchange = mock(McpAsyncServerExchange.class);
        when(asyncExchange.transportContext()).thenReturn(ctx);
        return new McpSyncServerExchange(asyncExchange);
    }

    @Test
    void copiesPatFromTransportContextIntoHolderForDurationOfCall() {
        AtomicReference<String> seen = new AtomicReference<>();
        SyncToolSpecification decorated = decorator.decorate(stub((ex, req) -> {
            seen.set(RequestPatHolder.get());
            return CallToolResult.builder().content(List.of(new TextContent("ok"))).build();
        }));

        McpSyncServerExchange exchange = exchangeWithContext(
                McpTransportContext.create(Map.of(KeltaTransportContextExtractor.PAT_KEY, "klt_propagated")));

        decorated.callHandler().apply(exchange, new CallToolRequest("stub", Map.of(), null));

        assertThat(seen.get()).isEqualTo("klt_propagated");
        assertThat(RequestPatHolder.get()).isNull(); // cleared after
    }

    @Test
    void clearsHolderEvenWhenInnerHandlerThrows() {
        SyncToolSpecification decorated = decorator.decorate(stub((ex, req) -> {
            throw new RuntimeException("boom");
        }));

        McpSyncServerExchange exchange = exchangeWithContext(
                McpTransportContext.create(Map.of(KeltaTransportContextExtractor.PAT_KEY, "klt_x")));

        try {
            decorated.callHandler().apply(exchange, new CallToolRequest("stub", Map.of(), null));
        } catch (RuntimeException ignored) {
        }

        assertThat(RequestPatHolder.get()).isNull();
    }

    @Test
    void doesNothingWhenContextHasNoPat() {
        AtomicReference<String> seen = new AtomicReference<>();
        SyncToolSpecification decorated = decorator.decorate(stub((ex, req) -> {
            seen.set(RequestPatHolder.get());
            return CallToolResult.builder().content(List.of(new TextContent("ok"))).build();
        }));

        McpSyncServerExchange exchange = exchangeWithContext(McpTransportContext.EMPTY);

        decorated.callHandler().apply(exchange, new CallToolRequest("stub", Map.of(), null));

        assertThat(seen.get()).isNull();
    }

    @Test
    void copiesTenantSlugFromTransportContextIntoHolder() {
        AtomicReference<String> seenPat = new AtomicReference<>();
        AtomicReference<String> seenSlug = new AtomicReference<>();
        SyncToolSpecification decorated = decorator.decorate(stub((ex, req) -> {
            seenPat.set(RequestPatHolder.get());
            seenSlug.set(RequestSlugHolder.get());
            return CallToolResult.builder().content(List.of(new TextContent("ok"))).build();
        }));

        McpSyncServerExchange exchange = exchangeWithContext(McpTransportContext.create(Map.of(
                KeltaTransportContextExtractor.PAT_KEY, "klt_session",
                KeltaTransportContextExtractor.TENANT_SLUG_KEY, "threadline-clothing")));

        decorated.callHandler().apply(exchange, new CallToolRequest("stub", Map.of(), null));

        assertThat(seenPat.get()).isEqualTo("klt_session");
        assertThat(seenSlug.get()).isEqualTo("threadline-clothing");
        assertThat(RequestPatHolder.get()).isNull();
        assertThat(RequestSlugHolder.get()).isNull();
    }
}
