package io.kelta.gateway.filter;

import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.auth.JwtAuthenticationFilter;
import io.kelta.gateway.auth.PublicPathMatcher;
import io.kelta.gateway.authz.cerbos.CerbosAuthorizationService;
import io.kelta.gateway.cache.GatewayCacheManager;
import io.kelta.gateway.config.TenantIpConfig;
import io.kelta.gateway.error.ResponseHelpers;
import io.kelta.gateway.geo.ClientIpResolver;
import io.kelta.gateway.metrics.GatewayMetrics;
import io.kelta.gateway.net.CidrBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Enforces per-tenant IP allowlists for data-path requests.
 *
 * <p>When a tenant has {@code ipAllowlistEnabled=true} with a non-empty CIDR list, a
 * request to {@code /api/**} is allowed only when <em>any</em> IP in its chain — the
 * socket remote address plus every {@code X-Forwarded-For} hop and {@code X-Real-IP}
 * (when {@code trust-forwarded-for} is enabled) — falls inside an allowed CIDR. Matching
 * "anywhere in the chain" is deliberately permissive so the check survives changes in the
 * proxy/Twingate topology; the trade-off is that {@code X-Forwarded-For} is client-supplied
 * and therefore spoofable — set {@code kelta.gateway.ip-allowlist.trust-forwarded-for=false}
 * to match the socket address only.
 *
 * <p><b>Fail-open by design</b> so a misconfiguration can never lock a tenant out:
 * <ul>
 *   <li>Global kill-switch off, no principal, non-{@code /api} path, or public path → allow.</li>
 *   <li>Tenant config missing from cache (worker unreachable at bootstrap) → allow.</li>
 *   <li>Restriction disabled or CIDR list empty → allow.</li>
 *   <li>Source IP matches → allow.</li>
 *   <li>Source IP does not match, but the user holds {@code MANAGE_TENANTS} (account admin)
 *       → allow (admin bypass).</li>
 *   <li>Otherwise → 403.</li>
 * </ul>
 *
 * <p>Ordered at -40: after {@code UserIdentityResolutionFilter} (-50) so the principal's
 * profile/tenant are resolved for the admin check, and before {@code RouteAuthorizationFilter}
 * (0) so out-of-range non-admins are rejected before the per-collection Cerbos check.
 */
@Component
public class TenantIpAllowlistFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantIpAllowlistFilter.class);

    /** System permission whose holders bypass the IP restriction (account/tenant admins). */
    private static final String BYPASS_PERMISSION = "MANAGE_TENANTS";

    private final GatewayCacheManager cacheManager;
    private final CerbosAuthorizationService cerbosService;
    private final PublicPathMatcher publicPathMatcher;
    private final GatewayMetrics metrics;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean trustForwardedFor;

    public TenantIpAllowlistFilter(
            GatewayCacheManager cacheManager,
            CerbosAuthorizationService cerbosService,
            PublicPathMatcher publicPathMatcher,
            GatewayMetrics metrics,
            ObjectMapper objectMapper,
            @Value("${kelta.gateway.ip-allowlist.enabled:true}") boolean enabled,
            @Value("${kelta.gateway.ip-allowlist.trust-forwarded-for:true}") boolean trustForwardedFor) {
        this.cacheManager = cacheManager;
        this.cerbosService = cerbosService;
        this.publicPathMatcher = publicPathMatcher;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        // Only guard data paths; UI shell/assets and public bootstrap endpoints pass through.
        if (!path.startsWith("/api/") || publicPathMatcher.isPublicRequest(exchange)) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        if (principal == null) {
            // Unauthenticated — RouteAuthorizationFilter will reject; no IP context to enforce.
            return chain.filter(exchange);
        }

        String tenantId = principal.getTenantId() != null
                ? principal.getTenantId()
                : TenantResolutionFilter.getTenantId(exchange);
        if (tenantId == null || tenantId.isBlank()) {
            return chain.filter(exchange);
        }

        TenantIpConfig config = cacheManager.getTenantIpConfig(tenantId).orElse(null);
        // Config not loaded (e.g. worker was unreachable at bootstrap) → fail open.
        if (config == null || !config.isRestricted()) {
            return chain.filter(exchange);
        }

        List<String> candidateIps = collectCandidateIps(exchange);
        if (matchesAnyCidr(candidateIps, config.getCidrs())) {
            return chain.filter(exchange);
        }

        // Out of range — admins bypass so a bad range never locks them out.
        return cerbosService.checkSystemPermission(principal, BYPASS_PERMISSION)
                .flatMap(isAdmin -> {
                    if (Boolean.TRUE.equals(isAdmin)) {
                        log.debug("IP {} outside allowlist for tenant {} but user {} is admin — allowing",
                                candidateIps, tenantId, principal.getUsername());
                        return chain.filter(exchange);
                    }
                    log.warn("Blocked user {} for tenant {}: source IPs {} outside allowlist {}",
                            principal.getUsername(), tenantId, candidateIps, config.getCidrs());
                    String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
                    String method = exchange.getRequest().getMethod() != null
                            ? exchange.getRequest().getMethod().name() : "unknown";
                    metrics.recordAuthzDenied(tenantSlug, "ip-allowlist", method);
                    return forbidden(exchange, "Access from your network is not permitted for this tenant");
                });
    }

    /**
     * Collects every candidate source IP for the request: the socket remote address plus,
     * when {@code trust-forwarded-for} is enabled, every {@code X-Forwarded-For} hop and
     * {@code X-Real-IP}. A match on any of them allows the request.
     */
    private List<String> collectCandidateIps(ServerWebExchange exchange) {
        List<String> ips = new ArrayList<>();

        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            addNormalized(ips, remote.getAddress().getHostAddress());
        }

        if (trustForwardedFor) {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                for (String hop : xff.split(",")) {
                    addNormalized(ips, hop);
                }
            }
            String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            addNormalized(ips, realIp);
        }

        return ips;
    }

    private void addNormalized(List<String> ips, String raw) {
        String ip = normalizeIp(raw);
        if (ip != null && !ips.contains(ip)) {
            ips.add(ip);
        }
    }

    /**
     * Normalizes a raw IP token: trims, strips an IPv6 scope suffix ({@code %eth0}) and
     * surrounding brackets ({@code [::1]}). Returns null for blanks.
     */
    static String normalizeIp(String raw) {
        return ClientIpResolver.normalizeIp(raw);
    }

    private boolean matchesAnyCidr(List<String> candidateIps, List<String> cidrs) {
        for (String cidr : cidrs) {
            CidrBlock parsed = CidrBlock.parse(cidr);
            if (parsed == null) {
                // Validated on write, but never let a bad range throw here.
                log.warn("Skipping invalid CIDR in allowlist: {}", cidr);
                continue;
            }
            for (String ip : candidateIps) {
                if (parsed.contains(ip)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        if (!ResponseHelpers.prepareJsonResponse(exchange.getResponse(), HttpStatus.FORBIDDEN)) {
            return Mono.empty();
        }

        ObjectNode error = objectMapper.createObjectNode();
        error.put("status", "403");
        error.put("code", "FORBIDDEN");
        error.put("detail", message);
        ObjectNode meta = error.putObject("meta");
        meta.put("path", exchange.getRequest().getPath().value());

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode errors = root.putArray("errors");
        errors.add(error);

        byte[] errorBytes;
        try {
            errorBytes = objectMapper.writeValueAsBytes(root);
        } catch (Exception e) {
            errorBytes = "{\"errors\":[{\"status\":\"403\",\"code\":\"FORBIDDEN\"}]}".getBytes(StandardCharsets.UTF_8);
        }

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(errorBytes)));
    }

    @Override
    public int getOrder() {
        return -40;
    }
}
