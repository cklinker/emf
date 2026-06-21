package io.kelta.worker.service;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.embedding.EmbeddingService;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs semantic (vector similarity) search over a collection's pgvector {@code VECTOR} field:
 * embeds the query text with the configured {@link EmbeddingService} and ranks records by cosine
 * distance via {@link StorageAdapter#semanticSearch}.
 *
 * <p>The response is a standard JSON:API collection so the read-side field-security advice strips
 * denied fields automatically; per-record distances are returned under top-level {@code meta} (not
 * stripped) keyed by record id.
 *
 * @since 1.0.0
 */
@Service
public class SemanticSearchService {

    private static final int DEFAULT_VECTOR_DIMENSION = 1536;
    private static final int MAX_LIMIT = 100;

    private final StorageAdapter storageAdapter;
    private final CollectionRegistry collectionRegistry;
    private final EmbeddingService embeddingService;

    public SemanticSearchService(StorageAdapter storageAdapter,
                                 CollectionRegistry collectionRegistry,
                                 EmbeddingService embeddingService) {
        this.storageAdapter = storageAdapter;
        this.collectionRegistry = collectionRegistry;
        this.embeddingService = embeddingService;
    }

    /** Thrown for client errors (unknown collection, no vector field, dimension mismatch). */
    public static class SemanticSearchException extends RuntimeException {
        public SemanticSearchException(String message) {
            super(message);
        }
    }

    /**
     * Searches {@code collectionName} for the records most similar to {@code queryText}.
     *
     * @return a JSON:API document ({@code data} + {@code meta.distances})
     */
    public Map<String, Object> search(String collectionName, String queryText, int limit) {
        if (queryText == null || queryText.isBlank()) {
            throw new SemanticSearchException("'query' is required");
        }
        CollectionDefinition definition = collectionRegistry.get(collectionName);
        if (definition == null) {
            throw new SemanticSearchException("Unknown collection: " + collectionName);
        }

        FieldDefinition vectorField = definition.fields().stream()
                .filter(f -> f.type() == FieldType.VECTOR)
                .findFirst()
                .orElseThrow(() -> new SemanticSearchException(
                        "Collection '" + collectionName + "' has no VECTOR field to search"));

        int fieldDimension = vectorDimension(vectorField);
        if (fieldDimension != embeddingService.dimensions()) {
            throw new SemanticSearchException(
                    "VECTOR field '" + vectorField.name() + "' has dimension " + fieldDimension
                            + " but the embedding provider (" + embeddingService.providerId()
                            + ") produces dimension " + embeddingService.dimensions()
                            + "; recreate the field with the matching dimension or configure a"
                            + " matching embedding provider.");
        }

        int effectiveLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        float[] queryEmbedding = embeddingService.embed(queryText);
        String literal = EmbeddingService.toVectorLiteral(queryEmbedding);

        List<Map<String, Object>> rows = storageAdapter.semanticSearch(
                definition, vectorField.effectiveColumnName(), literal, effectiveLimit, List.of());

        String vectorColumn = vectorField.effectiveColumnName();
        Map<String, Object> distances = new LinkedHashMap<>();
        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object distance = row.remove("_distance");
            row.remove(vectorColumn);            // never echo the raw embedding back
            row.remove(vectorField.name());
            Object id = row.get("id");
            if (id != null && distance != null) {
                distances.put(String.valueOf(id), distance);
            }
            records.add(row);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("distances", distances);
        meta.put("count", records.size());
        return JsonApiResponseBuilder.collection(collectionName, records, meta);
    }

    private static int vectorDimension(FieldDefinition field) {
        Object configured = field.getConfigValue("dimension");
        if (configured instanceof Number number) {
            return number.intValue();
        }
        if (configured instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return DEFAULT_VECTOR_DIMENSION;
            }
        }
        return DEFAULT_VECTOR_DIMENSION;
    }
}
