package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.SemanticSearchService;
import io.kelta.worker.service.SemanticSearchService.SemanticSearchException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Semantic (vector similarity) search over a collection's pgvector {@code VECTOR} field.
 *
 * <p>{@code POST /api/{collection}/semantic-search} with body {@code {"query": "...", "limit": 10}}.
 * The literal {@code semantic-search} segment makes this handler more specific than the generic
 * {@code /api/{collection}/{id}} router route, and it falls under the collection's existing gateway
 * route — so no new static route is needed.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/{collection}/semantic-search")
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    public SemanticSearchController(SemanticSearchService semanticSearchService) {
        this.semanticSearchService = semanticSearchService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> search(
            @PathVariable("collection") String collection,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> request = body == null ? Map.of() : body;
        Object queryObj = request.get("query");
        String query = queryObj == null ? null : queryObj.toString();
        int limit = request.get("limit") instanceof Number n ? n.intValue() : 10;

        try {
            return ResponseEntity.ok(semanticSearchService.search(collection, query, limit));
        } catch (SemanticSearchException e) {
            return ResponseEntity.badRequest().body(
                    JsonApiResponseBuilder.error("400", "SEMANTIC_SEARCH_INVALID",
                            "Semantic search failed", e.getMessage()));
        }
    }
}
