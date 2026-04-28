package io.kelta.mcp.transport;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC controller that dispatches MCP requests at
 * {@code /{tenantSlug}/mcp/(user|admin)} to the matching SDK transport
 * servlet, leaving the slug in the URL end-to-end.
 *
 * <p>The slug is just a Spring {@code @PathVariable} — Spring extracts
 * it, we stamp it onto the request as an attribute (read by
 * {@link KeltaTransportContextExtractor} when the SDK builds its
 * transport context), and we hand the request straight to the SDK
 * transport via {@code HttpServlet.service}. The SDK's {@code
 * mcpEndpoint} validation uses {@code requestUri.endsWith(mcpEndpoint)}
 * so the slug-prefixed URL matches natively.
 *
 * <p>This replaces an earlier strip-and-re-add approach where the
 * filter rewrote {@code /{slug}/mcp/user} to {@code /mcp/user} and
 * forwarded to a slug-less servlet registration. With path variables
 * we don't need to rewrite anything — the slug travels with the
 * request all the way through, and outbound calls re-use the same
 * slug verbatim.
 */
@RestController
public class KeltaMcpController {

    private final HttpServletStreamableServerTransportProvider userTransport;
    private final HttpServletStreamableServerTransportProvider adminTransport;

    public KeltaMcpController(
            @Qualifier("userTransportProvider")
            HttpServletStreamableServerTransportProvider userTransport,
            @Qualifier("adminTransportProvider")
            HttpServletStreamableServerTransportProvider adminTransport) {
        this.userTransport = userTransport;
        this.adminTransport = adminTransport;
    }

    @RequestMapping(
            value = "/{tenantSlug:[a-z][a-z0-9-]+}/mcp/{profile:user|admin}",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE}
    )
    public void dispatch(@PathVariable String tenantSlug,
                          @PathVariable String profile,
                          HttpServletRequest request,
                          HttpServletResponse response) throws Exception {
        request.setAttribute(KeltaTransportContextExtractor.SLUG_REQUEST_ATTRIBUTE, tenantSlug);
        HttpServletStreamableServerTransportProvider transport = "user".equals(profile)
                ? userTransport
                : adminTransport;
        transport.service(request, response);
    }
}
