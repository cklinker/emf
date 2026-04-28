package io.kelta.mcp.transport;

import io.kelta.mcp.auth.KeltaTransportContextExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring MVC controller that dispatches MCP requests at
 * {@code /{tenantSlug}/mcp/(user|admin)} to the matching stateless
 * transport, leaving the slug in the URL end-to-end.
 *
 * <p>The slug is just a Spring {@code @PathVariable} — Spring extracts
 * it, we stamp it onto the request as an attribute (read by
 * {@link KeltaTransportContextExtractor} when the SDK builds its
 * transport context), and the request goes straight to the
 * stateless transport's {@code service()} method.
 *
 * <p>Stateless mode (see {@link HttpServletStatelessServerTransportProvider})
 * means there's no per-pod session state to lose on restart. The
 * controller still accepts {@code GET} and {@code DELETE} so misbehaving
 * clients get a clean {@code 405 Method Not Allowed} from the transport
 * instead of a Spring 404.
 */
@RestController
public class KeltaMcpController {

    private final HttpServletStatelessServerTransportProvider userTransport;
    private final HttpServletStatelessServerTransportProvider adminTransport;

    public KeltaMcpController(
            @Qualifier("userTransportProvider")
            HttpServletStatelessServerTransportProvider userTransport,
            @Qualifier("adminTransportProvider")
            HttpServletStatelessServerTransportProvider adminTransport) {
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
        HttpServletStatelessServerTransportProvider transport = "user".equals(profile)
                ? userTransport
                : adminTransport;
        transport.service(request, response);
    }
}
