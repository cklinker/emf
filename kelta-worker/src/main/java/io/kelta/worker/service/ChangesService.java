package io.kelta.worker.service;

import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.repository.RecordTombstoneRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the offline-sync changes feed: returns the record deletions for a collection since a client
 * cursor, plus a fresh cursor. Upserts (created/updated records) are fetched by the client through
 * the normal JSON:API query path (`GET /api/{collection}?filter[updatedAt][gt]=<cursor>&sort=updatedAt`),
 * which already enforces Cerbos/FLS and handles timestamp binding — so this endpoint only adds what
 * that path can't express: which records were <em>deleted</em>.
 */
@Service
public class ChangesService {

    /** Thrown for an unknown collection (controller maps to 404). */
    public static class ChangesException extends RuntimeException {
        public ChangesException(String message) {
            super(message);
        }
    }

    private final CollectionRegistry collectionRegistry;
    private final RecordTombstoneRepository tombstoneRepository;

    public ChangesService(CollectionRegistry collectionRegistry,
                          RecordTombstoneRepository tombstoneRepository) {
        this.collectionRegistry = collectionRegistry;
        this.tombstoneRepository = tombstoneRepository;
    }

    /**
     * @param since the client's last cursor (exclusive); null/absent means an initial sync, for
     *              which there are no prior deletions to report.
     * @return {@code { deletions: [recordId…], deletionCount, cursor }} — pass {@code cursor} as the
     *         next {@code since} (and as {@code filter[updatedAt][gt]} for the upsert query).
     */
    public Map<String, Object> changes(String tenantId, String collectionName, Instant since) {
        if (collectionRegistry.get(collectionName) == null) {
            throw new ChangesException("Unknown collection: " + collectionName);
        }

        List<String> deletions = since == null
                ? List.of()
                : tombstoneRepository.findSince(tenantId, collectionName, since).stream()
                        .map(RecordTombstoneRepository.Tombstone::recordId)
                        .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletions", deletions);
        result.put("deletionCount", deletions.size());
        result.put("cursor", Instant.now().toString());
        return result;
    }
}
