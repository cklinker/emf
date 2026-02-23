package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.jsonapi.JsonApiDocument;
import com.emf.jsonapi.JsonApiParser;
import com.emf.jsonapi.ResourceObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Global filter that processes JSON:API include parameters on responses.
 *
 * Runs after the backend response (order -2) to:
 * 1. Process include parameters and resolve related resources from Redis cache
 * 2. Rebuild response with included resources
 *
 * Validates: Requirements 6.1, 6.6, 8.1-8.8
 */
@Component
public class FieldAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FieldAuthorizationFilter.class);

    private final JsonApiParser jsonApiParser;
    private final ObjectMapper objectMapper;
    private final IncludeResolver includeResolver;

    public FieldAuthorizationFilter(JsonApiParser jsonApiParser,
                                    ObjectMapper objectMapper,
                                    IncludeResolver includeResolver) {
        this.jsonApiParser = jsonApiParser;
        this.objectMapper = objectMapper;
        this.includeResolver = includeResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);

        log.debug("FieldAuthorizationFilter invoked for request: {} {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI());

        if (principal == null) {
            log.debug("No principal found, skipping response processing");
            return chain.filter(exchange);
        }

        // Check if there are include parameters — if not, skip response decoration entirely
        List<String> includeParams = parseIncludeParameter(exchange);
        if (includeParams.isEmpty()) {
            return chain.filter(exchange);
        }

        log.debug("Principal found: {}, decorating response for includes: {}", principal.getUsername(), includeParams);

        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                MediaType contentType = originalResponse.getHeaders().getContentType();
                if (contentType == null || !isJsonApiResponse(contentType)) {
                    log.debug("Response is not JSON:API, skipping include processing");
                    return super.writeWith(body);
                }

                Flux<DataBuffer> fluxBody = Flux.from(body);
                return DataBufferUtils.join(fluxBody)
                    .flatMap(dataBuffer -> {
                        try {
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);
                            String responseBody = new String(content, StandardCharsets.UTF_8);

                            JsonApiDocument document;
                            try {
                                document = jsonApiParser.parse(responseBody);
                            } catch (Exception e) {
                                log.warn("Failed to parse JSON:API response, returning original: {}", e.getMessage());
                                DataBuffer buffer = bufferFactory.wrap(content);
                                return super.writeWith(Mono.just(buffer));
                            }

                            if (document.hasErrors() || !document.hasData()) {
                                DataBuffer buffer = bufferFactory.wrap(content);
                                return super.writeWith(Mono.just(buffer));
                            }

                            log.debug("Processing include parameter: {}", includeParams);

                            return includeResolver.resolveIncludes(includeParams, document.getData())
                                .flatMap(includedResources -> {
                                    log.debug("Resolved {} included resources from cache", includedResources.size());

                                    if (!includedResources.isEmpty()) {
                                        if (document.hasIncluded()) {
                                            for (ResourceObject resource : includedResources) {
                                                document.addIncluded(resource);
                                            }
                                        } else {
                                            document.setIncluded(includedResources);
                                        }
                                    }

                                    DataBuffer modifiedBuffer = serializeDocument(document, bufferFactory, content);
                                    return super.writeWith(Mono.just(modifiedBuffer));
                                })
                                .onErrorResume(error -> {
                                    log.error("Error resolving includes: {}", error.getMessage(), error);
                                    DataBuffer buffer = bufferFactory.wrap(content);
                                    return super.writeWith(Mono.just(buffer));
                                });

                        } catch (Exception e) {
                            log.error("Error during response processing: {}", e.getMessage(), e);
                            return Mono.error(e);
                        }
                    });
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private boolean isJsonApiResponse(MediaType contentType) {
        return MediaType.APPLICATION_JSON.isCompatibleWith(contentType) ||
               "application/vnd.api+json".equalsIgnoreCase(contentType.toString());
    }

    private List<String> parseIncludeParameter(ServerWebExchange exchange) {
        List<String> includeValues = exchange.getRequest().getQueryParams().get("include");

        if (includeValues == null || includeValues.isEmpty()) {
            return Collections.emptyList();
        }

        String includeParam = includeValues.get(0);
        if (includeParam == null || includeParam.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] parts = includeParam.split(",");
        List<String> result = new ArrayList<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }

        return result;
    }

    private DataBuffer serializeDocument(JsonApiDocument document,
                                          DataBufferFactory bufferFactory,
                                          byte[] originalContent) {
        String filteredJson;
        try {
            StringBuilder json = new StringBuilder("{");

            if (document.hasData()) {
                json.append("\"data\":");
                if (document.isSingleResource() && document.getData().size() == 1) {
                    json.append(objectMapper.writeValueAsString(document.getData().get(0)));
                } else {
                    json.append(objectMapper.writeValueAsString(document.getData()));
                }
            } else {
                json.append("\"data\":null");
            }

            if (document.hasIncluded()) {
                json.append(",\"included\":");
                json.append(objectMapper.writeValueAsString(document.getIncluded()));
            }

            if (document.getMeta() != null && !document.getMeta().isEmpty()) {
                json.append(",\"meta\":");
                json.append(objectMapper.writeValueAsString(document.getMeta()));
            }

            if (document.hasErrors()) {
                json.append(",\"errors\":");
                json.append(objectMapper.writeValueAsString(document.getErrors()));
            }

            json.append("}");
            filteredJson = json.toString();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JSON:API document: {}", e.getMessage());
            return bufferFactory.wrap(originalContent);
        }

        byte[] bytes = filteredJson.getBytes(StandardCharsets.UTF_8);
        return bufferFactory.wrap(bytes);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
