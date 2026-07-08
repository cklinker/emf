package io.kelta.worker.service;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.model.FieldDefinition;
import io.kelta.runtime.model.FieldType;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.runtime.storage.StorageAdapter;
import io.kelta.worker.util.TenantContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Keeps {@code VECTOR} embeddings consistent with data-masking config.
 *
 * <p>{@code EmbeddingOnWriteHook} already refuses to embed a masking-configured source field, so
 * new writes are safe. But rows embedded <em>before</em> a source field gained masking keep vectors
 * derived from the (now-masked) plaintext — semantic search could then match against the masked
 * text by inference (the ranking is an oracle even though the returned values are masked). When a
 * field's masking config toggles, this service purges the dependent VECTOR columns so no stale
 * plaintext-derived vector survives; rows re-embed correctly on their next write (the hook skips a
 * masked source, so a masked field's vector stays {@code NULL}; an unmasked field re-embeds).
 *
 * @since 1.0.0
 */
@Service
public class VectorMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(VectorMaintenanceService.class);

    /** {@code fieldTypeConfig} key naming the source text field a VECTOR field embeds. */
    private static final String SOURCE_CONFIG_KEY = "embeddingSource";

    private final CollectionRegistry collectionRegistry;
    private final StorageAdapter storageAdapter;

    public VectorMaintenanceService(CollectionRegistry collectionRegistry, StorageAdapter storageAdapter) {
        this.collectionRegistry = collectionRegistry;
        this.storageAdapter = storageAdapter;
    }

    /**
     * Purges (sets {@code NULL}) every VECTOR field whose {@code embeddingSource} is {@code sourceField},
     * for all rows of the collection — called when {@code sourceField}'s masking config toggles.
     *
     * @param tenantId     the tenant whose data to purge
     * @param collectionName the collection containing the source + vector fields
     * @param sourceField  the field whose masking config changed
     */
    @Async
    public void purgeVectorsForSourceAsync(String tenantId, String collectionName, String sourceField) {
        if (tenantId == null || collectionName == null || sourceField == null) {
            return;
        }
        try {
            TenantContextUtils.withTenant(tenantId, () -> {
                CollectionDefinition definition = collectionRegistry.get(collectionName);
                if (definition == null) {
                    log.warn("Cannot purge vectors: collection '{}' not registered", collectionName);
                    return;
                }
                for (FieldDefinition field : definition.fields()) {
                    if (field.type() == FieldType.VECTOR && sourceField.equals(embeddingSource(field))) {
                        int cleared = storageAdapter.clearVectorColumn(definition, field.name());
                        log.info("Purged {} stale vector(s) in {}.{} (source '{}' masking toggled, tenant={})",
                                cleared, collectionName, field.name(), sourceField, tenantId);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to purge stale vectors for {}.{} (tenant={}): {}",
                    collectionName, sourceField, tenantId, e.getMessage());
        }
    }

    private static String embeddingSource(FieldDefinition field) {
        Object configured = field.getConfigValue(SOURCE_CONFIG_KEY);
        return (configured instanceof String s && !s.isBlank()) ? s.trim() : null;
    }
}
