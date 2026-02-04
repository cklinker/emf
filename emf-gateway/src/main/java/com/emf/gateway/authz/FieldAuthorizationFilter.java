package com.emf.gateway.authz;

import com.emf.gateway.auth.GatewayPrincipal;
import com.emf.gateway.auth.JwtAuthenticationFilter;
import com.emf.gateway.jsonapi.IncludeResolver;
import com.emf.gateway.jsonapi.JsonApiDocument;
import com.emf.gateway.jsonapi.JsonApiParser;
import com.emf.gateway.jsonapi.ResourceObject;
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
import org.springframework.http.HttpHeaders;
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
 * Global filter that enforces field-level authorization policies on JSON:API responses
 * and processes JSON:API include parameters.
 * 
 * Runs after the backend response (order 100) to:
 * 1. Process include parameters and resolve related resources from Redis cache
 * 2. Filter fields based on field policies for both primary data and included resources
 * 3. Rebuild response with filtered fields and included resources
 * 
 * This filter combines field authorization and include processing to avoid multiple
 * response decorators in the filter chain (which don't work properly in Spring Cloud Gateway).
 * 
 * Validates: Requirements 5.2, 5.3, 5.4, 5.5, 5.6, 6.1, 6.6, 8.1-8.8
 */
@Component
public class FieldAuthorizationFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(FieldAuthorizationFilter.class);
    
    private final AuthzConfigCache authzConfigCache;
    private final PolicyEvaluator policyEvaluator;
    private final JsonApiParser jsonApiParser;
    private final ObjectMapper objectMapper;
    private final IncludeResolver includeResolver;
    
    /**
     * Creates a new FieldAuthorizationFilter.
     *
     * @param authzConfigCache the authorization config cache
     * @param policyEvaluator the policy evaluator for checking permissions
     * @param jsonApiParser the JSON:API parser
     * @param objectMapper the Jackson object mapper for serialization
     * @param includeResolver the include resolver for fetching related resources
     */
    public FieldAuthorizationFilter(AuthzConfigCache authzConfigCache,
                                    PolicyEvaluator policyEvaluator,
                                    JsonApiParser jsonApiParser,
                                    ObjectMapper objectMapper,
                                    IncludeResolver includeResolver) {
        this.authzConfigCache = authzConfigCache;
        this.policyEvaluator = policyEvaluator;
        this.jsonApiParser = jsonApiParser;
        this.objectMapper = objectMapper;
        this.includeResolver = includeResolver;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get the authenticated principal (set by JwtAuthenticationFilter)
        GatewayPrincipal principal = JwtAuthenticationFilter.getPrincipal(exchange);
        
        log.debug("FieldAuthorizationFilter invoked for request: {} {}", 
                exchange.getRequest().getMethod(), exchange.getRequest().getURI());
        
        // If no principal, skip field filtering (authentication filter should have rejected)
        if (principal == null) {
            log.debug("No principal found, skipping field filtering");
            return chain.filter(exchange);
        }
        
        log.debug("Principal found: {}, decorating response", principal.getUsername());
        
        // Decorate the response to intercept the body
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // Only process JSON:API responses
                MediaType contentType = originalResponse.getHeaders().getContentType();
                if (contentType == null || !isJsonApiResponse(contentType)) {
                    log.debug("Response is not JSON:API, skipping field filtering");
                    return super.writeWith(body);
                }
                
                // Collect the response body
                Flux<DataBuffer> fluxBody = Flux.from(body);
                return DataBufferUtils.join(fluxBody)
                    .flatMap(dataBuffer -> {
                        try {
                            // Read the response body as string
                            byte[] content = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(content);
                            DataBufferUtils.release(dataBuffer);
                            String responseBody = new String(content, StandardCharsets.UTF_8);
                            
                            log.debug("Processing JSON:API response for field filtering");
                            
                            // Parse JSON:API document
                            JsonApiDocument document;
                            try {
                                document = jsonApiParser.parse(responseBody);
                            } catch (Exception e) {
                                log.warn("Failed to parse JSON:API response, returning original: {}", e.getMessage());
                                // Return original response if parsing fails
                                DataBuffer buffer = bufferFactory.wrap(content);
                                return super.writeWith(Mono.just(buffer));
                            }
                            
                            // If document has errors, don't filter
                            if (document.hasErrors()) {
                                log.debug("Response contains errors, skipping field filtering");
                                DataBuffer buffer = bufferFactory.wrap(content);
                                return super.writeWith(Mono.just(buffer));
                            }
                            
                            // Check for include query parameter and resolve includes
                            List<String> includeParams = parseIncludeParameter(exchange);
                            
                            if (!includeParams.isEmpty() && document.hasData()) {
                                log.debug("Processing include parameter: {}", includeParams);
                                
                                // Resolve includes from Redis cache and then process the complete document
                                return includeResolver.resolveIncludes(includeParams, document.getData())
                                    .flatMap(includedResources -> {
                                        log.debug("Resolved {} included resources from cache", includedResources.size());
                                        
                                        // Add included resources to document
                                        if (!includedResources.isEmpty()) {
                                            // Merge with existing included resources if any
                                            if (document.hasIncluded()) {
                                                // Add new resources to existing included array
                                                for (ResourceObject resource : includedResources) {
                                                    document.addIncluded(resource);
                                                }
                                            } else {
                                                // Set the included array
                                                document.setIncluded(includedResources);
                                            }
                                            
                                            log.debug("Added {} resources to included array", includedResources.size());
                                        }
                                        
                                        // Process field filtering and get the modified buffer
                                        DataBuffer modifiedBuffer = processDocumentAndGetBuffer(document, principal, bufferFactory, content);
                                        // Write using super.writeWith to pass it up the decorator chain
                                        return super.writeWith(Mono.just(modifiedBuffer));
                                    })
                                    .onErrorResume(error -> {
                                        log.error("Error resolving includes: {}", error.getMessage(), error);
                                        // Continue with field filtering even if include resolution fails
                                        DataBuffer modifiedBuffer = processDocumentAndGetBuffer(document, principal, bufferFactory, content);
                                        return super.writeWith(Mono.just(modifiedBuffer));
                                    });
                            } else {
                                // No includes to process, go straight to field filtering
                                DataBuffer modifiedBuffer = processDocumentAndGetBuffer(document, principal, bufferFactory, content);
                                return super.writeWith(Mono.just(modifiedBuffer));
                            }
                            
                        } catch (Exception e) {
                            log.error("Error during field filtering: {}", e.getMessage(), e);
                            // On error, try to return original response
                            return Mono.error(e);
                        }
                    });
            }
        };
        
        // Continue the filter chain with the decorated response
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
    
    /**
     * Checks if the response content type is JSON:API.
     */
    private boolean isJsonApiResponse(MediaType contentType) {
        // Check for application/vnd.api+json or application/json
        return MediaType.APPLICATION_JSON.isCompatibleWith(contentType) ||
               "application/vnd.api+json".equalsIgnoreCase(contentType.toString());
    }
    
    /**
     * Parses the include query parameter into a list of relationship names.
     * The include parameter is comma-separated (e.g., "author,comments").
     * 
     * @param exchange the server web exchange
     * @return list of relationship names to include
     */
    private List<String> parseIncludeParameter(ServerWebExchange exchange) {
        List<String> includeValues = exchange.getRequest().getQueryParams().get("include");
        
        if (includeValues == null || includeValues.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Take the first include parameter value and split by comma
        String includeParam = includeValues.get(0);
        if (includeParam == null || includeParam.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // Split by comma and trim whitespace
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
    
    /**
     * Processes the document (applies field filtering), serializes it, and returns the modified buffer.
     */
    private DataBuffer processDocumentAndGetBuffer(JsonApiDocument document,
                                                    GatewayPrincipal principal,
                                                    DataBufferFactory bufferFactory,
                                                    byte[] originalContent) {
        // Extract collection ID from the first resource's type
        String collectionId = extractCollectionId(document);
        if (collectionId == null) {
            log.debug("No collection ID found in response, returning original content");
            return bufferFactory.wrap(originalContent);
        }
        
        log.debug("Applying field filtering for collection: {}, user: {}",
                collectionId, principal.getUsername());
        
        // Check if document has been modified (has included resources)
        boolean documentModified = document.hasIncluded();
        
        // Lookup authorization config for the collection
        Optional<AuthzConfig> authzConfigOpt = authzConfigCache.getConfig(collectionId);
        
        if (authzConfigOpt.isEmpty()) {
            // No authorization config - no field filtering needed
            log.debug("No authorization config found for collection: {}", collectionId);
            
            // If document was modified (has included resources), serialize it
            if (documentModified) {
                return serializeDocument(document, bufferFactory, originalContent);
            }
            
            // Otherwise return original content
            log.debug("No modifications made, returning original content");
            return bufferFactory.wrap(originalContent);
        }
        
        AuthzConfig authzConfig = authzConfigOpt.get();
        List<FieldPolicy> fieldPolicies = authzConfig.getFieldPolicies();
        
        if (fieldPolicies.isEmpty()) {
            // No field policies - no filtering needed
            log.debug("No field policies found for collection: {}", collectionId);
            
            // If document was modified (has included resources), serialize it
            if (documentModified) {
                return serializeDocument(document, bufferFactory, originalContent);
            }
            
            // Otherwise return original content
            log.debug("No modifications made, returning original content");
            return bufferFactory.wrap(originalContent);
        }
        
        // Apply field filtering to data resources
        if (document.hasData()) {
            for (ResourceObject resource : document.getData()) {
                filterResourceFields(resource, fieldPolicies, principal);
            }
        }
        
        // Apply field filtering to included resources
        if (document.hasIncluded()) {
            for (ResourceObject resource : document.getIncluded()) {
                // Get field policies for this resource's type
                Optional<AuthzConfig> includedAuthzConfigOpt = authzConfigCache.getConfig(resource.getType());
                if (includedAuthzConfigOpt.isPresent()) {
                    List<FieldPolicy> includedFieldPolicies = includedAuthzConfigOpt.get().getFieldPolicies();
                    if (!includedFieldPolicies.isEmpty()) {
                        filterResourceFields(resource, includedFieldPolicies, principal);
                    }
                }
            }
        }
        
        log.debug("Field filtering completed for collection: {}", collectionId);
        
        // Serialize the filtered document
        return serializeDocument(document, bufferFactory, originalContent);
    }
    
    /**
     * Serializes the document to JSON and returns it as a DataBuffer.
     */
    private DataBuffer serializeDocument(JsonApiDocument document,
                                         DataBufferFactory bufferFactory,
                                         byte[] originalContent) {
        String filteredJson;
        try {
            log.debug("Document before serialization - hasIncluded: {}, included size: {}, isSingleResource: {}", 
                    document.hasIncluded(), 
                    document.getIncluded() != null ? document.getIncluded().size() : 0,
                    document.isSingleResource());
            
            // Build JSON manually to handle single resource vs collection
            StringBuilder json = new StringBuilder("{");
            
            // Serialize data field
            if (document.hasData()) {
                json.append("\"data\":");
                if (document.isSingleResource() && document.getData().size() == 1) {
                    // Single resource - serialize as object
                    json.append(objectMapper.writeValueAsString(document.getData().get(0)));
                } else {
                    // Collection - serialize as array
                    json.append(objectMapper.writeValueAsString(document.getData()));
                }
            } else {
                json.append("\"data\":null");
            }
            
            // Serialize included field if present
            if (document.hasIncluded()) {
                json.append(",\"included\":");
                json.append(objectMapper.writeValueAsString(document.getIncluded()));
            }
            
            // Serialize meta field if present
            if (document.getMeta() != null && !document.getMeta().isEmpty()) {
                json.append(",\"meta\":");
                json.append(objectMapper.writeValueAsString(document.getMeta()));
            }
            
            // Serialize errors field if present
            if (document.hasErrors()) {
                json.append(",\"errors\":");
                json.append(objectMapper.writeValueAsString(document.getErrors()));
            }
            
            json.append("}");
            filteredJson = json.toString();
            
            log.debug("Serialized JSON length: {}, contains 'included': {}", 
                    filteredJson.length(), 
                    filteredJson.contains("\"included\""));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize filtered JSON:API document: {}", e.getMessage());
            // Return original response if serialization fails
            return bufferFactory.wrap(originalContent);
        }
        
        // Return the serialized bytes as a buffer
        byte[] filteredBytes = filteredJson.getBytes(StandardCharsets.UTF_8);
        return bufferFactory.wrap(filteredBytes);
    }
    
    /**
     * Extracts the collection ID from the JSON:API document.
     * Uses the type field from the first resource in data.
     */
    private String extractCollectionId(JsonApiDocument document) {
        if (document.hasData() && !document.getData().isEmpty()) {
            ResourceObject firstResource = document.getData().get(0);
            return firstResource.getType();
        }
        return null;
    }
    
    /**
     * Filters fields from a resource object based on field policies.
     * Removes fields that the principal doesn't have permission to view.
     */
    private void filterResourceFields(ResourceObject resource,
                                      List<FieldPolicy> fieldPolicies,
                                      GatewayPrincipal principal) {
        if (resource.getAttributes() == null || resource.getAttributes().isEmpty()) {
            return;
        }
        
        // Build a map of field names to policies for quick lookup
        Map<String, FieldPolicy> policyMap = new HashMap<>();
        for (FieldPolicy policy : fieldPolicies) {
            policyMap.put(policy.getFieldName(), policy);
        }
        
        // Collect fields to remove
        List<String> fieldsToRemove = new ArrayList<>();
        
        for (String fieldName : resource.getAttributes().keySet()) {
            FieldPolicy policy = policyMap.get(fieldName);
            
            if (policy != null) {
                // Field has a policy - evaluate it
                boolean allowed = policyEvaluator.evaluate(policy, principal);
                
                if (!allowed) {
                    // Principal doesn't satisfy policy - remove field
                    fieldsToRemove.add(fieldName);
                    log.debug("Removing field '{}' from resource {}:{} - user {} lacks required roles: {}",
                            fieldName, resource.getType(), resource.getId(),
                            principal.getUsername(), policy.getRoles());
                }
            }
            // If no policy exists for the field, keep it (default allow)
        }
        
        // Remove filtered fields
        for (String fieldName : fieldsToRemove) {
            resource.removeAttribute(fieldName);
        }
        
        if (!fieldsToRemove.isEmpty()) {
            log.debug("Filtered {} fields from resource {}:{} for user {}",
                    fieldsToRemove.size(), resource.getType(), resource.getId(), principal.getUsername());
        }
    }
    
    @Override
    public int getOrder() {
        // Must run BEFORE NettyWriteResponseFilter (order -1) to intercept response body
        // NettyWriteResponseFilter writes the response to the client, so we need to
        // decorate the response before it gets written
        return -2; // Run after routing but before NettyWriteResponseFilter
    }
}
