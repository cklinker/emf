package com.emf.gateway.filter;

import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.jsonapi.JsonApiDocument;
import com.emf.jsonapi.JsonApiParser;
import com.emf.jsonapi.ResourceObject;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Global filter that resolves JSON:API {@code ?include=} parameters.
 *
 * <p>When a request to {@code /api/**} contains an {@code include} query parameter,
 * this filter intercepts the response from the backend, parses the JSON:API document,
 * resolves the requested included resources via {@link IncludeResolver}, and adds
 * them to the response's {@code included} array.
 *
 * <p>This filter applies to both system collections and user-defined collections
 * because all routes go through {@code /api/{collection}}.
 *
 * <p>Runs at order 10200 — after path rewriting (10100) but before
 * {@code NettyRoutingFilter} (MAX_VALUE).
 *
 * @since 1.0.0
 */
@Component
public class IncludeResolutionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IncludeResolutionFilter.class);

    private static final String API_PREFIX = "/api/";
    private static final String INCLUDE_PARAM = "include";

    private final IncludeResolver includeResolver;
    private final JsonApiParser jsonApiParser;

    public IncludeResolutionFilter(IncludeResolver includeResolver, JsonApiParser jsonApiParser) {
        this.includeResolver = includeResolver;
        this.jsonApiParser = jsonApiParser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();

        // Only apply to /api/ routes
        if (path == null || !path.startsWith(API_PREFIX)) {
            return chain.filter(exchange);
        }

        // Check if the request has an include parameter
        String includeParam = exchange.getRequest().getQueryParams().getFirst(INCLUDE_PARAM);
        if (includeParam == null || includeParam.isBlank()) {
            return chain.filter(exchange);
        }

        List<String> includeNames = Arrays.asList(includeParam.split(","));

        log.debug("Include resolution requested for path '{}': includes={}", path, includeNames);

        // Decorate the response to intercept and modify it
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // Only process JSON responses
                String contentType = getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                if (contentType == null || !contentType.contains("json")) {
                    return super.writeWith(body);
                }

                // Collect the response body
                return DataBufferUtils.join(Flux.from(body))
                        .flatMap(dataBuffer -> {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);

                            String responseJson = new String(content, StandardCharsets.UTF_8);

                            return resolveAndInjectIncludes(responseJson, includeNames)
                                    .flatMap(modifiedJson -> {
                                        byte[] modifiedBytes = modifiedJson.getBytes(StandardCharsets.UTF_8);
                                        // Update Content-Length header
                                        getHeaders().setContentLength(modifiedBytes.length);
                                        DataBuffer modifiedBuffer = bufferFactory.wrap(modifiedBytes);
                                        return super.writeWith(Mono.just(modifiedBuffer));
                                    });
                        });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * Parses the response JSON, resolves includes, and returns modified JSON.
     */
    private Mono<String> resolveAndInjectIncludes(String responseJson, List<String> includeNames) {
        try {
            JsonApiDocument document = jsonApiParser.parse(responseJson);

            if (!document.hasData() || document.getData().isEmpty()) {
                return Mono.just(responseJson);
            }

            return includeResolver.resolveIncludes(includeNames, document.getData())
                    .map(includedResources -> {
                        if (includedResources.isEmpty()) {
                            return responseJson;
                        }

                        // Add resolved resources to the document's included array
                        List<ResourceObject> existingIncluded = document.getIncluded();
                        if (existingIncluded != null) {
                            includedResources.addAll(existingIncluded);
                        }
                        document.setIncluded(includedResources);

                        try {
                            return jsonApiParser.serialize(document);
                        } catch (Exception e) {
                            log.warn("Failed to serialize modified JSON:API document: {}", e.getMessage());
                            return responseJson;
                        }
                    })
                    .onErrorResume(error -> {
                        log.warn("Include resolution failed, returning original response: {}", error.getMessage());
                        return Mono.just(responseJson);
                    });

        } catch (Exception e) {
            log.debug("Failed to parse response as JSON:API document, skipping include resolution: {}",
                    e.getMessage());
            return Mono.just(responseJson);
        }
    }

    @Override
    public int getOrder() {
        // After CollectionPathRewriteFilter (10100) but before NettyRoutingFilter (MAX_VALUE)
        // This filter modifies the RESPONSE, so its order in the filter chain
        // determines when the response decorator is registered.
        return 10200;
    }
}
