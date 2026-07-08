package io.kelta.runtime.storage;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.AggregationSpec;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Routes each storage operation to the {@link StorageAdapter} that backs the target
 * collection, so a tenant can mix Kelta-owned physical tables with externally-backed
 * (REST / foreign-JDBC) collections behind the same SPI.
 *
 * <p>Selection is driven by {@code definition.storageConfig().adapterConfig().get("adapterType")}:
 * <ul>
 *   <li>absent / blank / {@code "physical-table"} → the physical table adapter (the default);</li>
 *   <li>any other value → the registered {@link ExternalStorageAdapter} whose
 *       {@link StorageAdapter#storageType()} matches;</li>
 *   <li>an unknown value → falls back to the physical adapter (fail-safe: an
 *       unresolved external type never silently routes to the wrong store).</li>
 * </ul>
 *
 * <p>This bean is {@link Primary} so engines that inject {@code StorageAdapter} get the
 * router. Consumers that inject {@link PhysicalTableStorageAdapter} concretely
 * (e.g. unique-constraint checks) bypass routing and keep talking to the physical store.
 *
 * @since 1.0.0
 */
@Service
@Primary
public class DispatchingStorageAdapter implements StorageAdapter {

    static final String ADAPTER_TYPE_KEY = "adapterType";
    static final String PHYSICAL_TYPE = "physical-table";

    private final StorageAdapter physical;
    private final Map<String, StorageAdapter> byType;

    public DispatchingStorageAdapter(
            PhysicalTableStorageAdapter physical,
            List<ExternalStorageAdapter> externalAdapters) {
        this.physical = physical;
        Map<String, StorageAdapter> registry = new HashMap<>();
        registry.put(physical.storageType(), physical);
        for (ExternalStorageAdapter adapter : externalAdapters) {
            registry.put(adapter.storageType(), adapter);
        }
        this.byType = Map.copyOf(registry);
    }

    /**
     * Resolve the adapter backing the given collection. Package-private for testing.
     */
    StorageAdapter adapterFor(CollectionDefinition definition) {
        String type = PHYSICAL_TYPE;
        if (definition != null && definition.storageConfig() != null) {
            String configured = definition.storageConfig().adapterConfig().get(ADAPTER_TYPE_KEY);
            if (configured != null && !configured.isBlank()) {
                type = configured;
            }
        }
        StorageAdapter resolved = byType.get(type);
        return resolved != null ? resolved : physical;
    }

    @Override
    public String storageType() {
        return "dispatching";
    }

    @Override
    public void initializeCollection(CollectionDefinition definition) {
        adapterFor(definition).initializeCollection(definition);
    }

    @Override
    public void updateCollectionSchema(CollectionDefinition oldDefinition, CollectionDefinition newDefinition) {
        adapterFor(newDefinition).updateCollectionSchema(oldDefinition, newDefinition);
    }

    @Override
    public QueryResult query(CollectionDefinition definition, QueryRequest request) {
        return adapterFor(definition).query(definition, request);
    }

    @Override
    public Map<String, Object> aggregate(CollectionDefinition definition,
                                         List<FilterCondition> filters,
                                         List<AggregationSpec> specs) {
        return adapterFor(definition).aggregate(definition, filters, specs);
    }

    @Override
    public List<Map<String, Object>> semanticSearch(CollectionDefinition definition,
                                                     String vectorColumn,
                                                     String queryVectorLiteral,
                                                     int limit,
                                                     List<FilterCondition> filters) {
        return adapterFor(definition).semanticSearch(definition, vectorColumn, queryVectorLiteral, limit, filters);
    }

    @Override
    public Optional<Map<String, Object>> getById(CollectionDefinition definition, String id) {
        return adapterFor(definition).getById(definition, id);
    }

    @Override
    public Map<String, Object> create(CollectionDefinition definition, Map<String, Object> data) {
        return adapterFor(definition).create(definition, data);
    }

    @Override
    public Optional<Map<String, Object>> update(CollectionDefinition definition, String id, Map<String, Object> data) {
        return adapterFor(definition).update(definition, id, data);
    }

    @Override
    public boolean delete(CollectionDefinition definition, String id) {
        return adapterFor(definition).delete(definition, id);
    }

    @Override
    public boolean isUnique(CollectionDefinition definition, String fieldName, Object value, String excludeId) {
        return adapterFor(definition).isUnique(definition, fieldName, value, excludeId);
    }

    @Override
    public int clearVectorColumn(CollectionDefinition definition, String fieldName) {
        return adapterFor(definition).clearVectorColumn(definition, fieldName);
    }
}
