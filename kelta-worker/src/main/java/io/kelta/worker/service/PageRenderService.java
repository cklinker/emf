package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.registry.CollectionRegistry;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a published custom page into a versioned {@link PageRenderContract}. Looks up the
 * {@code ui-pages} record by {@code slug} that is {@code published} and {@code active} in the
 * current tenant (resolved by tenant context + RLS), and returns its component tree. Unpublished,
 * inactive, or unknown slugs resolve to empty (the controller maps that to 404), so draft pages are
 * never served to end users.
 */
@Service
public class PageRenderService {

    private static final Logger log = LoggerFactory.getLogger(PageRenderService.class);

    /** Render-contract version — bumped when the component-tree node schema changes incompatibly. */
    static final String CONTRACT_VERSION = "1.0";
    private static final String UI_PAGES = "ui-pages";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;
    private final ObjectMapper objectMapper;

    public PageRenderService(QueryEngine queryEngine, CollectionRegistry collectionRegistry,
                             ObjectMapper objectMapper) {
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
        this.objectMapper = objectMapper;
    }

    public Optional<PageRenderContract> render(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        CollectionDefinition definition = collectionRegistry.get(UI_PAGES);
        if (definition == null) {
            return Optional.empty();
        }

        List<FilterCondition> filters = List.of(
                FilterCondition.eq("slug", slug),
                FilterCondition.eq("published", true),
                FilterCondition.eq("active", true));
        QueryRequest request = new QueryRequest(new Pagination(1, 1), List.of(), List.of(), filters);
        QueryResult result = queryEngine.executeQuery(definition, request);
        if (result.data().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> page = result.data().getFirst();
        return Optional.of(new PageRenderContract(
                CONTRACT_VERSION,
                asString(page.get("slug")),
                asString(page.get("title")),
                asString(page.get("path")),
                extractTree(page.get("config"))));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** The builder's component tree is stored in the page's {@code config} JSON (object or string). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractTree(Object config) {
        if (config instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (config instanceof String str && !str.isBlank()) {
            try {
                return objectMapper.readValue(str, MAP_TYPE);
            } catch (RuntimeException e) {
                log.warn("Failed to parse ui-page config JSON: {}", e.getMessage());
            }
        }
        return Map.of();
    }
}
