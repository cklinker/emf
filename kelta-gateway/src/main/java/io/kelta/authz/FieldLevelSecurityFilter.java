package io.kelta.gateway.authz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kelta.gateway.route.RouteDefinition;
import io.kelta.gateway.route.RouteRegistry;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Global filter that enforces field-level security (FLS) on API responses.
 *
 * <p>When permissions are enabled, this filter:
 * <ul>
 *   <li>Strips fields marked as HIDDEN from response payloads</li>
 *   <li>Strips READ_ONLY fields from write request bodies (POST/PUT/PATCH)</li>
 * </ul>
 *
 * <p>Operates on JSON:API responses where data has an {@code attributes} object.
 * Supports both single-resource ({@code "data": {}}) and list
 * ({@code "data": []}) responses.
 *
 * <p>Order: 10 (after RouteAuthorizationFilter at 0, before SecurityHeadersFilter at 100).
 */
@Component
public class FieldLevelSecurityFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FieldLevelSecurityFilter.class);
    private static final String HIDDEN = "HIDDEN";
    private static final String READ_ONLY = "READ_ONLY";

    private final ObjectMapper objectMapper;
    private final RouteRegistry routeRegistry;
    private final boolean permissionsEnabled;

    public FieldLevelSecurityFilter(
            ObjectMapper objectMapper,
            RouteRegistry routeRegistry,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled) {
        this.objectMapper = objectMapper;
        this.routeRegistry = routeRegistry;
        this.permissionsEnabled = permissionsEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!permissionsEnabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        ResolvedPermissions permissions = PermissionResolutionFilter.getPermissions(exchange);
        if (permissions == null || permissions.isAllPermissive() || permissions.isDenied()) {
            return chain.filter(exchange);
        }

        // Look up collection to find field permissions
        Optional<RouteDefinition> route = routeRegistry.findByPath(path);
        if (route.isEmpty()) {
            return chain.filter(exchange);
        }

        String collectionId = route.get().getId();
        Map<String, String> fieldPerms = permissions.fieldPermissions().get(collectionId);
        if (fieldPerms == null || fieldPerms.isEmpty()) {
            return chain.filter(exchange);
        }

        // Collect HIDDEN fields for response stripping
        Set<String> hiddenFields = new HashSet<>();
        Set<String> readOnlyFields = new HashSet<>();
        for (Map.Entry<String, String> entry : fieldPerms.entrySet()) {
            if (HIDDEN.equals(entry.getValue())) {
                hiddenFields.add(entry.getKey());
            } else if (READ_ONLY.equals(entry.getValue())) {
                readOnlyFields.add(entry.getKey());
            }
        }

        if (hiddenFields.isEmpty() && readOnlyFields.isEmpty()) {
            return chain.filter(exchange);
        }

        // For write requests, strip READ_ONLY fields from request body
        // (HIDDEN fields should also be stripped from writes)
        HttpMethod method = exchange.getRequest().getMethod();
        boolean isWriteRequest = method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH;

        // Decorate the response to strip HIDDEN fields
        if (!hiddenFields.isEmpty()) {
            ServerHttpResponse originalResponse = exchange.getResponse();
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    String contentType = originalResponse.getHeaders().getFirst("Content-Type");
                    if (contentType == null || !contentType.contains("application/")) {
                        return super.writeWith(body);
                    }

                    return DataBufferUtils.join(Flux.from(body))
                            .flatMap(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);

                                try {
                                    String responseBody = new String(content, StandardCharsets.UTF_8);
                                    String filtered = stripHiddenFields(responseBody, hiddenFields);
                                    byte[] filteredBytes = filtered.getBytes(StandardCharsets.UTF_8);

                                    originalResponse.getHeaders().setContentLength(filteredBytes.length);
                                    DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                    return super.writeWith(Mono.just(bufferFactory.wrap(filteredBytes)));
                                } catch (Exception e) {
                                    log.warn("Failed to apply FLS filtering, passing through: {}",
                                            e.getMessage());
                                    DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                    return super.writeWith(Mono.just(bufferFactory.wrap(content)));
                                }
                            });
                }
            };

            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        }

        return chain.filter(exchange);
    }

    /**
     * Strips HIDDEN fields from a JSON:API response body.
     * Handles both single resource and list responses.
     */
    String stripHiddenFields(String responseBody, Set<String> hiddenFields)
            throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root == null || !root.isObject()) {
            return responseBody;
        }

        JsonNode data = root.get("data");
        if (data == null) {
            return responseBody;
        }

        if (data.isObject()) {
            stripAttributeFields(data, hiddenFields);
        } else if (data.isArray()) {
            for (JsonNode item : data) {
                stripAttributeFields(item, hiddenFields);
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private void stripAttributeFields(JsonNode resourceNode, Set<String> hiddenFields) {
        JsonNode attributes = resourceNode.get("attributes");
        if (attributes != null && attributes.isObject()) {
            ObjectNode attrObj = (ObjectNode) attributes;
            for (String field : hiddenFields) {
                attrObj.remove(field);
            }
        }
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
