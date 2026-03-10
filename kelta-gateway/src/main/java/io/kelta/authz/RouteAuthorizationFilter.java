package io.kelta.gateway.authz;

import io.kelta.gateway.auth.GatewayPrincipal;
import io.kelta.gateway.auth.JwtAuthenticationFilter;
import io.kelta.gateway.auth.PublicPathMatcher;
import io.kelta.gateway.filter.RequestLoggingFilter;
import io.kelta.gateway.filter.TenantResolutionFilter;
import io.kelta.gateway.metrics.GatewayMetrics;
import io.kelta.gateway.route.RouteDefinition;
import io.kelta.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Global filter that enforces route-level authorization.
 *
 * <p>When {@code kelta.gateway.security.permissions-enabled} is false,
 * this filter only checks that the user is authenticated (valid JWT required).
 * Defaults to enabled (true).
 *
 * <p>When permissions are enabled, this filter additionally checks object-level
 * permissions for API paths:
 * <ul>
 *   <li>GET/HEAD/OPTIONS require {@code canRead}</li>
 *   <li>POST requires {@code canCreate}</li>
 *   <li>PUT/PATCH require {@code canEdit}</li>
 *   <li>DELETE requires {@code canDelete}</li>
 * </ul>
 *
 * <p>System permissions {@code VIEW_ALL_DATA} and {@code MODIFY_ALL_DATA} serve
 * as overrides for read and write operations respectively.
 *
 * <p>Actuator and internal paths are excluded from authentication requirements.
 */
@Component
public class RouteAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationFilter.class);

    private final RouteRegistry routeRegistry;
    private final boolean permissionsEnabled;
    private final PublicPathMatcher publicPathMatcher;
    private final GatewayMetrics metrics;
    private final ObjectMapper objectMapper;

    public RouteAuthorizationFilter(
            RouteRegistry routeRegistry,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled,
            PublicPathMatcher publicPathMatcher,
            GatewayMetrics metrics,
            ObjectMapper objectMapper) {
        this.routeRegistry = routeRegistry;
        this.permissionsEnabled = permissionsEnabled;
        this.publicPathMatcher = publicPathMatcher;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Allow public paths through without principal check
        if (publicPathMatcher.isPublicRequest(exchange)) {
            return chain.filter(exchange);
        }

        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);

        if (principal == null) {
            log.warn("No principal found in exchange for path: {}", path);
            String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
            String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "unknown";
            metrics.recordAuthzDenied(tenantSlug, "unknown", method);
            return forbidden(exchange, "Authentication required");
        }

        log.debug("Authenticated user: {} accessing path: {}", principal.getUsername(), path);

        // If permissions enforcement is disabled, allow all authenticated users
        if (!permissionsEnabled) {
            // Still set route attribute for metrics if we can find the route
            setRouteAttribute(exchange, path);
            return chain.filter(exchange);
        }

        // Check object-level permissions for API paths
        if (path.startsWith("/api/")) {
            return checkObjectPermissions(exchange, chain, path);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> checkObjectPermissions(ServerWebExchange exchange,
                                               GatewayFilterChain chain,
                                               String path) {
        ResolvedPermissions permissions = PermissionResolutionFilter.getPermissions(exchange);
        if (permissions == null) {
            // No permissions resolved and no tenant context — deny access
            log.warn("No permissions resolved for path: {}", path);
            String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
            String methodStr = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "unknown";
            metrics.recordAuthzDenied(tenantSlug, "unknown", methodStr);
            return forbidden(exchange, "Permission resolution unavailable");
        }

        // Deny if permission resolution failed (fail-closed)
        if (permissions.isDenied()) {
            log.warn("Permission resolution failed (fail-closed) for path: {}", path);
            String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
            String methodStr = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "unknown";
            metrics.recordAuthzDenied(tenantSlug, "unknown", methodStr);
            return forbidden(exchange, "Permission resolution unavailable");
        }

        // All-permissive bypasses all checks (platform admin, disabled)
        if (permissions.isAllPermissive()) {
            setRouteAttribute(exchange, path);
            return chain.filter(exchange);
        }

        // Check API_ACCESS system permission
        if (!permissions.hasSystemPermission("API_ACCESS")) {
            log.warn("User denied API access (no API_ACCESS permission) for path: {}", path);
            String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
            String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "unknown";
            metrics.recordAuthzDenied(tenantSlug, "unknown", method);
            return forbidden(exchange, "API access not permitted");
        }

        // Look up the route to get collection info
        Optional<RouteDefinition> route = routeRegistry.findByPath(path);
        if (route.isEmpty()) {
            // No route found — not a collection API call, allow through
            return chain.filter(exchange);
        }

        String collectionId = route.get().getId();
        String collectionName = route.get().getCollectionName();
        ObjectPermissions objPerms = permissions.getObjectPermissions(collectionId);
        HttpMethod method = exchange.getRequest().getMethod();

        // Set route attribute for RequestLoggingFilter metrics
        exchange.getAttributes().put(RequestLoggingFilter.ROUTE_ATTR, collectionName);

        boolean allowed = isAllowed(method, objPerms);

        // System permission overrides
        if (!allowed) {
            if (isReadMethod(method)) {
                allowed = permissions.hasSystemPermission("VIEW_ALL_DATA");
            } else {
                allowed = permissions.hasSystemPermission("MODIFY_ALL_DATA");
            }
        }

        if (!allowed) {
            log.warn("User denied {} on collection '{}' (id={}): insufficient object permissions",
                    method, collectionName, collectionId);
            String tenantSlug = TenantResolutionFilter.getTenantSlug(exchange);
            String methodStr = method != null ? method.name() : "unknown";
            metrics.recordAuthzDenied(tenantSlug, collectionName, methodStr);
            return forbidden(exchange,
                    "Insufficient permissions for " + method + " on " + collectionName);
        }

        return chain.filter(exchange);
    }

    /**
     * Sets the route attribute on the exchange for downstream metric recording.
     * Looks up the route by path and stores the collection name.
     */
    private void setRouteAttribute(ServerWebExchange exchange, String path) {
        if (path != null && path.startsWith("/api/")) {
            routeRegistry.findByPath(path)
                    .ifPresent(route -> exchange.getAttributes()
                            .put(RequestLoggingFilter.ROUTE_ATTR, route.getCollectionName()));
        }
    }

    private boolean isAllowed(HttpMethod method, ObjectPermissions perms) {
        if (method == null) return false;
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return perms.canRead();
        }
        if (method == HttpMethod.POST) {
            return perms.canCreate();
        }
        if (method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            return perms.canEdit();
        }
        if (method == HttpMethod.DELETE) {
            return perms.canDelete();
        }
        return false;
    }

    private boolean isReadMethod(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        // Use Jackson to safely serialize — prevents JSON injection via untrusted
        // values in message or request path.
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
            Mono.just(exchange.getResponse().bufferFactory().wrap(errorBytes))
        );
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
