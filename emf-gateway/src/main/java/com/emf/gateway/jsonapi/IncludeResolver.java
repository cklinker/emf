package com.emf.gateway.jsonapi;

import com.emf.gateway.route.RouteDefinition;
import com.emf.gateway.route.RouteRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves JSON:API included resources from Redis cache with fallback to backend service.
 * 
 * This component processes include query parameters and fetches related resources:
 * 1. First tries to fetch from Redis cache (key pattern: "jsonapi:{type}:{id}")
 * 2. On cache miss, fetches from backend service and caches the result
 * 3. Returns all successfully resolved resources
 */
@Component
public class IncludeResolver {
    private static final Logger log = LoggerFactory.getLogger(IncludeResolver.class);
    private static final String REDIS_KEY_PREFIX = "jsonapi:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RouteRegistry routeRegistry;
    private final WebClient webClient;
    private final JsonApiParser jsonApiParser;

    public IncludeResolver(ReactiveRedisTemplate<String, String> redisTemplate,
                          RouteRegistry routeRegistry,
                          WebClient.Builder webClientBuilder,
                          JsonApiParser jsonApiParser) {
        this.redisTemplate = redisTemplate;
        this.routeRegistry = routeRegistry;
        this.webClient = webClientBuilder.build();
        this.jsonApiParser = jsonApiParser;
    }

    /**
     * Resolves included resources based on include parameters and primary data.
     * 
     * Algorithm:
     * 1. Parse include query parameter (comma-separated relationship names)
     * 2. Extract relationships from primary data resources
     * 3. For each relationship, build Redis key: "jsonapi:{type}:{id}"
     * 4. Lookup resources in Redis (non-blocking)
     * 5. Collect found resources, skip missing with logged cache miss
     * 6. Return list of resolved resources
     * 
     * @param includeParams List of relationship names to include (e.g., ["author", "comments"])
     * @param primaryData List of primary data resources to extract relationships from
     * @return Mono containing list of resolved ResourceObjects
     */
    public Mono<List<ResourceObject>> resolveIncludes(
            List<String> includeParams,
            List<ResourceObject> primaryData) {
        
        // If no include params or no primary data, return empty list
        if (includeParams == null || includeParams.isEmpty() || 
            primaryData == null || primaryData.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        // Extract all resource identifiers from relationships in primary data
        Set<ResourceIdentifier> resourceIdentifiers = extractResourceIdentifiers(includeParams, primaryData);

        if (resourceIdentifiers.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        // Lookup each resource identifier in Redis
        return Flux.fromIterable(resourceIdentifiers)
                .flatMap(this::lookupResourceInRedis)
                .collect(Collectors.toList());
    }

    /**
     * Extracts resource identifiers from relationships in primary data.
     * Only extracts identifiers for relationships specified in includeParams.
     *
     * <p>Matching is attempted in three stages:
     * <ol>
     *   <li>Exact match on relationship key (e.g. include=category matches relationships.category)</li>
     *   <li>Case-insensitive match on relationship key (e.g. include=Category matches relationships.category)</li>
     *   <li>Match by target collection type: if the include name matches the {@code data.type} of any
     *       relationship, that relationship is included. This allows {@code ?include=categories} to resolve
     *       a relationship keyed as {@code category_id} whose target type is {@code categories}.</li>
     * </ol>
     *
     * @param includeParams List of relationship names to include
     * @param primaryData List of primary data resources
     * @return Set of unique resource identifiers
     */
    private Set<ResourceIdentifier> extractResourceIdentifiers(
            List<String> includeParams,
            List<ResourceObject> primaryData) {

        Set<ResourceIdentifier> identifiers = new HashSet<>();

        for (ResourceObject resource : primaryData) {
            Map<String, Relationship> relationships = resource.getRelationships();
            if (relationships == null || relationships.isEmpty()) {
                continue;
            }

            // For each requested include parameter
            for (String includeName : includeParams) {
                List<Relationship> matched = new ArrayList<>();

                // 1. Exact match on relationship key
                Relationship exactMatch = relationships.get(includeName);
                if (exactMatch != null) {
                    matched.add(exactMatch);
                }

                // 2. Fallback: case-insensitive match on relationship keys
                if (matched.isEmpty()) {
                    for (Map.Entry<String, Relationship> entry : relationships.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(includeName)) {
                            matched.add(entry.getValue());
                            break;
                        }
                    }
                }

                // 3. Fallback: match by target collection type (data.type)
                //    e.g. include=categories matches a relationship whose data.type is "categories"
                if (matched.isEmpty()) {
                    for (Relationship rel : relationships.values()) {
                        if (rel.isSingleResource()) {
                            ResourceIdentifier rid = rel.getDataAsSingle();
                            if (rid != null && includeName.equalsIgnoreCase(rid.getType())) {
                                matched.add(rel);
                            }
                        } else if (rel.isResourceCollection()) {
                            List<ResourceIdentifier> collection = rel.getDataAsCollection();
                            if (collection != null && !collection.isEmpty()) {
                                ResourceIdentifier first = collection.get(0);
                                if (first != null && includeName.equalsIgnoreCase(first.getType())) {
                                    matched.add(rel);
                                }
                            }
                        }
                    }
                }

                // Extract identifiers from all matched relationships
                for (Relationship relationship : matched) {
                    if (relationship.isSingleResource()) {
                        ResourceIdentifier identifier = relationship.getDataAsSingle();
                        if (identifier != null && identifier.getType() != null && identifier.getId() != null) {
                            identifiers.add(identifier);
                        }
                    } else if (relationship.isResourceCollection()) {
                        List<ResourceIdentifier> collection = relationship.getDataAsCollection();
                        if (collection != null) {
                            for (ResourceIdentifier identifier : collection) {
                                if (identifier != null && identifier.getType() != null && identifier.getId() != null) {
                                    identifiers.add(identifier);
                                }
                            }
                        }
                    }
                }
            }
        }

        return identifiers;
    }

    /**
     * Looks up a resource in Redis by its type and id.
     * On cache miss, fetches from backend service and caches the result.
     * 
     * @param identifier Resource identifier with type and id
     * @return Mono containing the ResourceObject if found, or empty Mono if not found
     */
    private Mono<ResourceObject> lookupResourceInRedis(ResourceIdentifier identifier) {
        String redisKey = buildRedisKey(identifier.getType(), identifier.getId());
        
        return redisTemplate.opsForValue()
                .get(redisKey)
                .flatMap(json -> {
                    try {
                        // Deserialize JSON to ResourceObject
                        ResourceObject resource = jsonApiParser.parseResourceObject(json);
                        log.debug("Cache hit for resource: {}:{}", identifier.getType(), identifier.getId());
                        return Mono.just(resource);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached resource {}:{}: {}", 
                                identifier.getType(), identifier.getId(), e.getMessage());
                        // On deserialization error, try fetching from backend
                        return fetchFromBackendAndCache(identifier);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss for resource: {}:{} - fetching from backend", 
                            identifier.getType(), identifier.getId());
                    return fetchFromBackendAndCache(identifier);
                }))
                .doOnError(error -> {
                    log.error("Failed to resolve resource {}:{}: {}", 
                            identifier.getType(), identifier.getId(), error.getMessage());
                })
                .onErrorResume(error -> {
                    // Gracefully handle errors - continue without the resource
                    return Mono.empty();
                });
    }
    
    /**
     * Fetches a resource from the backend service and caches it in Redis.
     * 
     * @param identifier Resource identifier with type and id
     * @return Mono containing the ResourceObject if found, or empty Mono if not found
     */
    private Mono<ResourceObject> fetchFromBackendAndCache(ResourceIdentifier identifier) {
        // Get the backend service URL for this resource type (collection name)
        String collectionName = identifier.getType();
        
        // Find the route for this collection
        return Mono.fromCallable(() -> {
                    return routeRegistry.getAllRoutes().stream()
                            .filter(route -> collectionName.equals(route.getCollectionName()))
                            .findFirst();
                })
                .flatMap(routeOpt -> {
                    if (routeOpt.isEmpty()) {
                        log.warn("No route found for collection: {}", collectionName);
                        return Mono.empty();
                    }
                    
                    RouteDefinition route = routeOpt.get();
                    
                    // Build the backend URL: {backendUrl}/api/collections/{type}/{id}
                    String backendUrl = route.getBackendUrl() + "/api/collections/" + collectionName + "/" + identifier.getId();
                    
                    log.debug("Fetching resource {}:{} from backend: {}", 
                            identifier.getType(), identifier.getId(), backendUrl);
                    
                    // Make request to backend service
                    return webClient.get()
                            .uri(backendUrl)
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(responseJson -> {
                                try {
                                    // Parse the JSON:API response
                                    JsonApiDocument document = jsonApiParser.parse(responseJson);
                                    
                                    // Extract the resource from the data field
                                    if (document.hasData() && !document.getData().isEmpty()) {
                                        ResourceObject resource = document.getData().get(0);
                                        
                                        log.debug("Successfully fetched resource {}:{} from backend", 
                                                identifier.getType(), identifier.getId());
                                        
                                        // Cache the resource for future requests
                                        return cacheResource(resource)
                                                .thenReturn(resource);
                                    } else {
                                        log.debug("Backend returned empty data for resource {}:{}", 
                                                identifier.getType(), identifier.getId());
                                        return Mono.empty();
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to parse backend response for resource {}:{}: {}", 
                                            identifier.getType(), identifier.getId(), e.getMessage());
                                    return Mono.empty();
                                }
                            })
                            .onErrorResume(error -> {
                                log.warn("Backend request failed for resource {}:{}: {}", 
                                        identifier.getType(), identifier.getId(), error.getMessage());
                                return Mono.empty();
                            });
                });
    }
    
    /**
     * Caches a resource in Redis with TTL.
     * 
     * @param resource The resource to cache
     * @return Mono that completes when caching is done
     */
    private Mono<Boolean> cacheResource(ResourceObject resource) {
        String redisKey = buildRedisKey(resource.getType(), resource.getId());
        
        try {
            // Serialize the resource to JSON
            String json = jsonApiParser.serializeResourceObject(resource);
            
            // Store in Redis with TTL
            return redisTemplate.opsForValue()
                    .set(redisKey, json, CACHE_TTL)
                    .doOnSuccess(success -> {
                        if (success) {
                            log.debug("Cached resource {}:{} with TTL {}",
                                    resource.getType(), resource.getId(), CACHE_TTL);
                        } else {
                            log.warn("Failed to cache resource {}:{}", 
                                    resource.getType(), resource.getId());
                        }
                    })
                    .onErrorResume(error -> {
                        log.warn("Redis caching failed for resource {}:{}: {}", 
                                resource.getType(), resource.getId(), error.getMessage());
                        // Don't fail the request if caching fails
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize resource {}:{} for caching: {}", 
                    resource.getType(), resource.getId(), e.getMessage());
            return Mono.just(false);
        }
    }

    /**
     * Builds a Redis key for a resource.
     * Pattern: "jsonapi:{type}:{id}"
     * 
     * @param type Resource type
     * @param id Resource id
     * @return Redis key string
     */
    private String buildRedisKey(String type, String id) {
        return REDIS_KEY_PREFIX + type + ":" + id;
    }
}
