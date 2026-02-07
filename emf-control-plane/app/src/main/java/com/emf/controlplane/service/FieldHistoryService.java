package com.emf.controlplane.service;

import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.FieldHistory;
import com.emf.controlplane.repository.FieldHistoryRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Records field-level changes and provides history queries.
 * Called from the storage adapter after successful updates to track
 * changes to fields with track_history=true.
 */
@Service
public class FieldHistoryService {

    private static final Logger log = LoggerFactory.getLogger(FieldHistoryService.class);

    private final FieldHistoryRepository fieldHistoryRepository;
    private final FieldRepository fieldRepository;
    private final ObjectMapper objectMapper;

    public FieldHistoryService(
            FieldHistoryRepository fieldHistoryRepository,
            FieldRepository fieldRepository,
            ObjectMapper objectMapper) {
        this.fieldHistoryRepository = fieldHistoryRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Compares old and new record values, creates field_history entries
     * for each changed field that has track_history=true.
     */
    @Transactional
    public void recordChanges(String tenantId, String collectionId, String recordId,
                               Map<String, Object> oldRecord, Map<String, Object> newRecord,
                               String userId, String changeSource) {
        try {
            List<Field> trackedFields = fieldRepository
                    .findByCollectionIdAndTrackHistoryTrueAndActiveTrue(collectionId);

            if (trackedFields.isEmpty()) {
                return;
            }

            for (Field field : trackedFields) {
                Object oldVal = oldRecord.get(field.getName());
                Object newVal = newRecord.get(field.getName());

                if (!Objects.equals(oldVal, newVal)) {
                    FieldHistory history = new FieldHistory();
                    history.setTenantId(tenantId);
                    history.setCollectionId(collectionId);
                    history.setRecordId(recordId);
                    history.setFieldName(field.getName());
                    history.setOldValue(serializeToJson(oldVal));
                    history.setNewValue(serializeToJson(newVal));
                    history.setChangedBy(userId != null ? userId : "SYSTEM");
                    history.setChangedAt(Instant.now());
                    history.setChangeSource(changeSource != null ? changeSource : "API");
                    fieldHistoryRepository.save(history);
                }
            }
        } catch (Exception e) {
            // History recording failures should not block the update
            log.warn("Failed to record field history for record '{}' in collection '{}': {}",
                    recordId, collectionId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<FieldHistory> getRecordHistory(String collectionId, String recordId,
                                                Pageable pageable) {
        return fieldHistoryRepository.findByCollectionIdAndRecordIdOrderByChangedAtDesc(
                collectionId, recordId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FieldHistory> getFieldHistory(String collectionId, String recordId,
                                               String fieldName, Pageable pageable) {
        return fieldHistoryRepository.findByCollectionIdAndRecordIdAndFieldNameOrderByChangedAtDesc(
                collectionId, recordId, fieldName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FieldHistory> getFieldHistoryAcrossRecords(String collectionId,
                                                            String fieldName, Pageable pageable) {
        return fieldHistoryRepository.findByCollectionIdAndFieldNameOrderByChangedAtDesc(
                collectionId, fieldName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<FieldHistory> getUserHistory(String userId, Pageable pageable) {
        return fieldHistoryRepository.findByChangedByOrderByChangedAtDesc(userId, pageable);
    }

    private String serializeToJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize value to JSON: {}", e.getMessage());
            return String.valueOf(value);
        }
    }
}
