package io.kelta.mcp.transport;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP adapter for {@link McpStatelessServerTransport} — turns one inbound
 * POST into one JSON-RPC request/response (or a fire-and-forget notification),
 * with no per-session state.
 *
 * <p>Why stateless: the SDK's
 * {@code HttpServletStreamableServerTransportProvider} keeps a per-pod
 * {@code ConcurrentHashMap<sessionId, McpStreamableServerSession>}. Pod
 * restarts wipe that map, and the next request from a connected client
 * arrives carrying a stale {@code Mcp-Session-Id} — the SDK then returns
 * {@code -32603 Session not found}, {@code mcp-remote 0.1.x} surfaces it as
 * a fatal error, and the client tools start failing until the user manually
 * restarts the MCP host. Painful with frequent deploys.
 *
 * <p>The kelta MCP surface doesn't actually need streaming: every tool is a
 * synchronous request/response, and we don't broadcast {@code listChanged}
 * notifications or use resource subscriptions. So we drop the streamable
 * transport in favour of the SDK's built-in stateless mode
 * ({@link McpStatelessServerTransport} +
 * {@link io.modelcontextprotocol.server.McpStatelessSyncServer}) and provide
 * this tiny HTTP adapter — the SDK ships one for stdio but not for
 * Servlet-based HTTP. Each request carries everything it needs (PAT in
 * {@code Authorization}, tenant slug in the URL path), so pod restarts are
 * invisible to the client by construction.
 *
 * <p>The class is not a {@code HttpServlet}; it just exposes
 * {@link #service(HttpServletRequest, HttpServletResponse)} so that
 * {@code KeltaMcpController} can dispatch to it by type, the same way it
 * called {@code transport.service(...)} on the previous streamable
 * transport.
 *
 * <h2>Wire format</h2>
 * <ul>
 *   <li>{@code POST} with a JSON-RPC request body → blocks on the SDK
 *       handler, writes a {@code 200 OK} JSON-RPC response.</li>
 *   <li>{@code POST} with a JSON-RPC notification body (no {@code id}) →
 *       fires the handler, returns {@code 204 No Content}.</li>
 *   <li>{@code GET} (used by the streamable transport for SSE) and
 *       {@code DELETE} (session close) → {@code 405 Method Not Allowed},
 *       since stateless mode has nothing to stream and no session to
 *       close.</li>
 * </ul>
 *
 * <p>JSON-RPC batches (an array body) are not supported — {@code mcp-remote}
 * sends individual messages and the protocol marks batch as optional. A
 * batch yields {@code 400 Bad Request} with a clear error.
 */
public class HttpServletStatelessServerTransportProvider implements McpStatelessServerTransport {

    private static final Logger log = LoggerFactory.getLogger(
            HttpServletStatelessServerTransportProvider.class);
    private static final String APPLICATION_JSON = "application/json";

    private final McpJsonMapper jsonMapper;
    private final McpTransportContextExtractor<HttpServletRequest> contextExtractor;
    private volatile McpStatelessServerHandler handler;

    public HttpServletStatelessServerTransportProvider(
            McpTransportContextExtractor<HttpServletRequest> contextExtractor) {
        this.jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        this.contextExtractor = contextExtractor != null
                ? contextExtractor
                : (req) -> McpTransportContext.EMPTY;
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler handler) {
        this.handler = handler;
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.empty();
    }

    /**
     * Servlet-side dispatch. Reads the request body as a JSON-RPC message,
     * routes to the SDK handler, and writes the response.
     *
     * <p>Exceptions thrown by the handler are mapped to JSON-RPC error
     * responses or HTTP error statuses — they never propagate out of this
     * method.
     */
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (handler == null) {
            // McpServer.sync(transport).build() registers the handler; if we
            // haven't been built yet, the transport isn't usable.
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "MCP handler not bound");
            return;
        }

        String method = req.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            // Stateless mode has no SSE channel and no session lifecycle, so
            // GET / DELETE have nothing meaningful to do. Return 405 with an
            // Allow header so well-behaved clients don't keep retrying.
            resp.setHeader("Allow", "POST");
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    method + " not supported by stateless MCP transport");
            return;
        }

        byte[] body = req.getInputStream().readAllBytes();
        if (body.length == 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Empty request body");
            return;
        }

        // Parse polymorphically. JSON-RPC requests have an `id`, notifications
        // don't. We could deserialize directly to the sealed JSONRPCMessage
        // interface, but going through a Map<?,?> avoids any reliance on the
        // SDK's polymorphic-deserialization wiring.
        Object parsed;
        try {
            parsed = jsonMapper.readValue(body, Object.class);
        } catch (IOException e) {
            log.warn("Malformed JSON-RPC body", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed JSON");
            return;
        }

        if (parsed instanceof java.util.List<?>) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "JSON-RPC batch requests are not supported by this transport");
            return;
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "JSON-RPC payload must be an object");
            return;
        }

        McpTransportContext context = contextExtractor.extract(req);

        try {
            if (raw.containsKey("id") && raw.get("id") != null) {
                McpSchema.JSONRPCRequest request = jsonMapper.convertValue(
                        raw, McpSchema.JSONRPCRequest.class);
                McpSchema.JSONRPCResponse response = handler.handleRequest(context, request)
                        .block();
                writeJsonResponse(resp, response);
            } else {
                McpSchema.JSONRPCNotification notification = jsonMapper.convertValue(
                        raw, McpSchema.JSONRPCNotification.class);
                handler.handleNotification(context, notification).block();
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } catch (RuntimeException e) {
            // The SDK handler should produce a JSONRPCResponse with an error
            // payload rather than throw; if anything escapes, surface it as a
            // 500 with a redacted message rather than leak a stack trace.
            log.error("Unhandled exception while dispatching MCP request", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal MCP server error");
        }
    }

    private void writeJsonResponse(HttpServletResponse resp, Object payload) throws IOException {
        String json = jsonMapper.writeValueAsString(payload);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(APPLICATION_JSON + ";charset=" + StandardCharsets.UTF_8.name());
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = resp.getWriter()) {
            writer.write(json);
        }
    }
}
