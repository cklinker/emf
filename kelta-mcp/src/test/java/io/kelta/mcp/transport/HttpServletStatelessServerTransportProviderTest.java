package io.kelta.mcp.transport;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServletStatelessServerTransportProviderTest {

    private static final String CTX_KEY = "kelta.test.value";

    private HttpServletStatelessServerTransportProvider transport;
    private AtomicReference<JSONRPCRequest> seenRequest;
    private AtomicReference<JSONRPCNotification> seenNotification;
    private AtomicReference<McpTransportContext> seenContext;

    @BeforeEach
    void setUp() {
        transport = new HttpServletStatelessServerTransportProvider(req -> {
            String marker = req.getHeader("X-Test-Marker");
            return marker == null
                    ? McpTransportContext.EMPTY
                    : McpTransportContext.create(Map.of(CTX_KEY, marker));
        });
        seenRequest = new AtomicReference<>();
        seenNotification = new AtomicReference<>();
        seenContext = new AtomicReference<>();
        transport.setMcpHandler(new McpStatelessServerHandler() {
            @Override
            public Mono<JSONRPCResponse> handleRequest(McpTransportContext context, JSONRPCRequest request) {
                seenRequest.set(request);
                seenContext.set(context);
                return Mono.just(new JSONRPCResponse(
                        "2.0",
                        request.id(),
                        Map.of("ok", true, "echo", request.method()),
                        null));
            }

            @Override
            public Mono<Void> handleNotification(McpTransportContext context, JSONRPCNotification notification) {
                seenNotification.set(notification);
                seenContext.set(context);
                return Mono.empty();
            }
        });
    }

    @Test
    void postRequestReturnsJsonRpcResponseAndPropagatesContext() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.addHeader("Content-Type", "application/json");
        req.addHeader("X-Test-Marker", "from-extractor");
        req.setContent("""
                {"jsonrpc":"2.0","id":7,"method":"tools/list","params":{}}""".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getContentAsString())
                .contains("\"id\":7")
                .contains("\"echo\":\"tools/list\"");

        assertThat(seenRequest.get().method()).isEqualTo("tools/list");
        assertThat(seenContext.get()).isNotNull();
        assertThat(seenContext.get().get(CTX_KEY)).isEqualTo("from-extractor");
    }

    @Test
    void postNotificationReturns204NoContent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.addHeader("Content-Type", "application/json");
        req.setContent("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}""".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(204);
        assertThat(res.getContentAsString()).isEmpty();
        assertThat(seenNotification.get().method()).isEqualTo("notifications/initialized");
    }

    @Test
    void postWithJsonRpcRequestThatHasNullIdIsTreatedAsNotification() throws Exception {
        // Some clients send id=null for fire-and-forget. We treat that as a
        // notification rather than dispatching as a request — JSON-RPC 2.0
        // requires an id on requests, and a null id means "no response
        // expected".
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.addHeader("Content-Type", "application/json");
        req.setContent("""
                {"jsonrpc":"2.0","id":null,"method":"notifications/cancelled"}""".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(204);
    }

    @Test
    void getReturns405MethodNotAllowed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/threadline-clothing/mcp/user");
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(405);
        assertThat(res.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void deleteReturns405MethodNotAllowed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/threadline-clothing/mcp/user");
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(405);
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.addHeader("Content-Type", "application/json");
        req.setContent(new byte[0]);
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(400);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.addHeader("Content-Type", "application/json");
        req.setContent("{not json".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(400);
    }

    @Test
    void jsonRpcBatchReturns400() throws Exception {
        // JSON-RPC batches are optional in the protocol; we don't support them
        // and reject explicitly so callers see a clear error rather than
        // silently dropping requests.
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.addHeader("Content-Type", "application/json");
        req.setContent("""
                [{"jsonrpc":"2.0","id":1,"method":"tools/list"},
                 {"jsonrpc":"2.0","id":2,"method":"resources/list"}]""".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();

        transport.service(req, res);

        assertThat(res.getStatus()).isEqualTo(400);
        assertThat(seenRequest.get()).isNull();
    }

    @Test
    void serviceBeforeHandlerBoundReturns503() throws Exception {
        // Before McpServer.sync(transport).build() runs, setMcpHandler hasn't
        // been called — any request should get 503 rather than NPE.
        HttpServletStatelessServerTransportProvider unbound =
                new HttpServletStatelessServerTransportProvider(null);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/threadline-clothing/mcp/user");
        req.setContent("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list"}""".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();

        unbound.service(req, res);

        assertThat(res.getStatus()).isEqualTo(503);
    }

    @Test
    void closeGracefullyCompletesSynchronously() {
        // Stateless mode has no streams or session state to drain — the
        // close-graceful contract is just a Mono<Void> that completes.
        Void done = transport.closeGracefully().block();
        assertThat(done).isNull();
    }
}
