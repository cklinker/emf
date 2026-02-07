package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateRecordTypeRequest;
import com.emf.controlplane.dto.SetPicklistOverrideRequest;
import com.emf.controlplane.dto.UpdateRecordTypeRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.RecordType;
import com.emf.controlplane.entity.RecordTypePicklist;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.CollectionRepository;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.RecordTypePicklistRepository;
import com.emf.controlplane.repository.RecordTypeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RecordTypeService {

    private static final Logger log = LoggerFactory.getLogger(RecordTypeService.class);

    private final RecordTypeRepository recordTypeRepository;
    private final RecordTypePicklistRepository recordTypePicklistRepository;
    private final CollectionRepository collectionRepository;
    private final FieldRepository fieldRepository;
    private final ObjectMapper objectMapper;

    public RecordTypeService(
            RecordTypeRepository recordTypeRepository,
            RecordTypePicklistRepository recordTypePicklistRepository,
            CollectionRepository collectionRepository,
            FieldRepository fieldRepository,
            ObjectMapper objectMapper) {
        this.recordTypeRepository = recordTypeRepository;
        this.recordTypePicklistRepository = recordTypePicklistRepository;
        this.collectionRepository = collectionRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<RecordType> listRecordTypes(String collectionId) {
        verifyCollection(collectionId);
        return recordTypeRepository.findByCollectionIdOrderByNameAsc(collectionId);
    }

    @Transactional
    public RecordType createRecordType(String collectionId, String tenantId,
                                        CreateRecordTypeRequest request) {
        log.info("Creating record type '{}' for collection: {}", request.getName(), collectionId);

        Collection collection = verifyCollection(collectionId);

        if (recordTypeRepository.existsByTenantIdAndCollectionIdAndName(
                tenantId, collectionId, request.getName())) {
            throw new DuplicateResourceException("RecordType", "name", request.getName());
        }

        RecordType recordType = new RecordType(tenantId, collection, request.getName());
        recordType.setDescription(request.getDescription());

        if (request.isDefault()) {
            clearDefaultForCollection(collectionId);
            recordType.setDefault(true);
        }

        recordType = recordTypeRepository.save(recordType);
        log.info("Created record type '{}' with id: {}", request.getName(), recordType.getId());
        return recordType;
    }

    @Transactional(readOnly = true)
    public RecordType getRecordType(String recordTypeId) {
        return recordTypeRepository.findById(recordTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RecordType", recordTypeId));
    }

    @Transactional
    public RecordType updateRecordType(String recordTypeId, UpdateRecordTypeRequest request) {
        log.info("Updating record type: {}", recordTypeId);

        RecordType recordType = recordTypeRepository.findById(recordTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RecordType", recordTypeId));

        if (request.getName() != null && !request.getName().equals(recordType.getName())) {
            if (recordTypeRepository.existsByTenantIdAndCollectionIdAndName(
                    recordType.getTenantId(), recordType.getCollection().getId(), request.getName())) {
                throw new DuplicateResourceException("RecordType", "name", request.getName());
            }
            recordType.setName(request.getName());
        }

        if (request.getDescription() != null) {
            recordType.setDescription(request.getDescription());
        }

        if (request.getActive() != null) {
            recordType.setActive(request.getActive());
        }

        if (request.getIsDefault() != null) {
            if (request.getIsDefault()) {
                clearDefaultForCollection(recordType.getCollection().getId());
            }
            recordType.setDefault(request.getIsDefault());
        }

        return recordTypeRepository.save(recordType);
    }

    @Transactional
    public void deleteRecordType(String recordTypeId) {
        log.info("Deleting record type: {}", recordTypeId);
        RecordType recordType = recordTypeRepository.findById(recordTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RecordType", recordTypeId));
        recordTypeRepository.delete(recordType);
    }

    // --- Picklist Override Management ---

    @Transactional(readOnly = true)
    public List<RecordTypePicklist> getPicklistOverrides(String recordTypeId) {
        recordTypeRepository.findById(recordTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RecordType", recordTypeId));
        return recordTypePicklistRepository.findByRecordTypeId(recordTypeId);
    }

    @Transactional
    public RecordTypePicklist setPicklistOverride(String recordTypeId, String fieldId,
                                                   SetPicklistOverrideRequest request) {
        log.info("Setting picklist override for record type: {}, field: {}", recordTypeId, fieldId);

        RecordType recordType = recordTypeRepository.findById(recordTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("RecordType", recordTypeId));

        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));

        // Validate field is PICKLIST or MULTI_PICKLIST type
        String type = field.getType().toUpperCase();
        if (!"PICKLIST".equals(type) && !"MULTI_PICKLIST".equals(type)) {
            throw new ValidationException("fieldId",
                    "Field must be of type PICKLIST or MULTI_PICKLIST, got: " + field.getType());
        }

        // Validate default value is in available values
        if (request.getDefaultValue() != null && !request.getDefaultValue().isBlank()) {
            if (!request.getAvailableValues().contains(request.getDefaultValue())) {
                throw new ValidationException("defaultValue",
                        "Default value must be one of the available values");
            }
        }

        String availableValuesJson;
        try {
            availableValuesJson = objectMapper.writeValueAsString(request.getAvailableValues());
        } catch (JsonProcessingException e) {
            throw new ValidationException("availableValues", "Failed to serialize values: " + e.getMessage());
        }

        Optional<RecordTypePicklist> existing = recordTypePicklistRepository
                .findByRecordTypeIdAndFieldId(recordTypeId, fieldId);

        RecordTypePicklist override;
        if (existing.isPresent()) {
            override = existing.get();
            override.setAvailableValues(availableValuesJson);
            override.setDefaultValue(request.getDefaultValue());
        } else {
            override = new RecordTypePicklist(recordType, field, availableValuesJson);
            override.setDefaultValue(request.getDefaultValue());
        }

        return recordTypePicklistRepository.save(override);
    }

    @Transactional
    public void removePicklistOverride(String recordTypeId, String fieldId) {
        log.info("Removing picklist override for record type: {}, field: {}", recordTypeId, fieldId);

        RecordTypePicklist override = recordTypePicklistRepository
                .findByRecordTypeIdAndFieldId(recordTypeId, fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("RecordTypePicklist",
                        recordTypeId + "/" + fieldId));

        recordTypePicklistRepository.delete(override);
    }

    @Transactional(readOnly = true)
    public Optional<RecordType> getDefaultRecordType(String collectionId) {
        return recordTypeRepository.findByCollectionIdAndIsDefaultTrue(collectionId);
    }

    private void clearDefaultForCollection(String collectionId) {
        recordTypeRepository.findByCollectionIdAndIsDefaultTrue(collectionId)
                .ifPresent(existing -> {
                    existing.setDefault(false);
                    recordTypeRepository.save(existing);
                });
    }

    private Collection verifyCollection(String collectionId) {
        return collectionRepository.findByIdAndActiveTrue(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection", collectionId));
    }
}
