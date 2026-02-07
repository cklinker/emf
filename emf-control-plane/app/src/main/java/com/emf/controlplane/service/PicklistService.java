package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateGlobalPicklistRequest;
import com.emf.controlplane.dto.PicklistValueRequest;
import com.emf.controlplane.dto.UpdateGlobalPicklistRequest;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.GlobalPicklist;
import com.emf.controlplane.entity.PicklistDependency;
import com.emf.controlplane.entity.PicklistValue;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.exception.ValidationException;
import com.emf.controlplane.repository.FieldRepository;
import com.emf.controlplane.repository.GlobalPicklistRepository;
import com.emf.controlplane.repository.PicklistDependencyRepository;
import com.emf.controlplane.repository.PicklistValueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PicklistService {

    private static final Logger log = LoggerFactory.getLogger(PicklistService.class);
    private static final String SOURCE_FIELD = "FIELD";
    private static final String SOURCE_GLOBAL = "GLOBAL";

    private final GlobalPicklistRepository globalPicklistRepository;
    private final PicklistValueRepository picklistValueRepository;
    private final PicklistDependencyRepository picklistDependencyRepository;
    private final FieldRepository fieldRepository;
    private final ObjectMapper objectMapper;

    public PicklistService(
            GlobalPicklistRepository globalPicklistRepository,
            PicklistValueRepository picklistValueRepository,
            PicklistDependencyRepository picklistDependencyRepository,
            FieldRepository fieldRepository,
            ObjectMapper objectMapper) {
        this.globalPicklistRepository = globalPicklistRepository;
        this.picklistValueRepository = picklistValueRepository;
        this.picklistDependencyRepository = picklistDependencyRepository;
        this.fieldRepository = fieldRepository;
        this.objectMapper = objectMapper;
    }

    // --- Global Picklist Management ---

    @Transactional(readOnly = true)
    public List<GlobalPicklist> listGlobalPicklists(String tenantId) {
        return globalPicklistRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public GlobalPicklist getGlobalPicklist(String id) {
        return globalPicklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlobalPicklist", id));
    }

    @Transactional
    @SetupAudited(section = "Picklists", entityType = "GlobalPicklist")
    public GlobalPicklist createGlobalPicklist(String tenantId, CreateGlobalPicklistRequest request) {
        log.info("Creating global picklist '{}' for tenant: {}", request.getName(), tenantId);

        if (globalPicklistRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new DuplicateResourceException("GlobalPicklist", "name", request.getName());
        }

        GlobalPicklist picklist = new GlobalPicklist(tenantId, request.getName());
        picklist.setDescription(request.getDescription());
        picklist.setSorted(request.isSorted());
        picklist.setRestricted(request.isRestricted());
        picklist = globalPicklistRepository.save(picklist);

        if (request.getValues() != null && !request.getValues().isEmpty()) {
            savePicklistValues(SOURCE_GLOBAL, picklist.getId(), request.getValues());
        }

        return picklist;
    }

    @Transactional
    @SetupAudited(section = "Picklists", entityType = "GlobalPicklist")
    public GlobalPicklist updateGlobalPicklist(String id, UpdateGlobalPicklistRequest request) {
        log.info("Updating global picklist: {}", id);

        GlobalPicklist picklist = globalPicklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlobalPicklist", id));

        if (request.getName() != null && !request.getName().equals(picklist.getName())) {
            if (globalPicklistRepository.existsByTenantIdAndName(picklist.getTenantId(), request.getName())) {
                throw new DuplicateResourceException("GlobalPicklist", "name", request.getName());
            }
            picklist.setName(request.getName());
        }
        if (request.getDescription() != null) {
            picklist.setDescription(request.getDescription());
        }
        if (request.getSorted() != null) {
            picklist.setSorted(request.getSorted());
        }
        if (request.getRestricted() != null) {
            picklist.setRestricted(request.getRestricted());
        }

        return globalPicklistRepository.save(picklist);
    }

    @Transactional
    @SetupAudited(section = "Picklists", entityType = "GlobalPicklist")
    public void deleteGlobalPicklist(String id) {
        log.info("Deleting global picklist: {}", id);

        GlobalPicklist picklist = globalPicklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GlobalPicklist", id));

        // Delete associated values
        picklistValueRepository.deleteByPicklistSourceTypeAndPicklistSourceId(SOURCE_GLOBAL, id);
        globalPicklistRepository.delete(picklist);
    }

    // --- Picklist Value Management ---

    @Transactional(readOnly = true)
    public List<PicklistValue> getGlobalPicklistValues(String globalPicklistId) {
        globalPicklistRepository.findById(globalPicklistId)
                .orElseThrow(() -> new ResourceNotFoundException("GlobalPicklist", globalPicklistId));
        return picklistValueRepository
                .findByPicklistSourceTypeAndPicklistSourceIdAndActiveTrueOrderBySortOrderAsc(
                        SOURCE_GLOBAL, globalPicklistId);
    }

    @Transactional
    public List<PicklistValue> setGlobalPicklistValues(String globalPicklistId, List<PicklistValueRequest> values) {
        log.info("Setting {} values for global picklist: {}", values.size(), globalPicklistId);
        globalPicklistRepository.findById(globalPicklistId)
                .orElseThrow(() -> new ResourceNotFoundException("GlobalPicklist", globalPicklistId));
        return replacePicklistValues(SOURCE_GLOBAL, globalPicklistId, values);
    }

    @Transactional(readOnly = true)
    public List<PicklistValue> getFieldPicklistValues(String fieldId) {
        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));

        // Check if field uses a global picklist
        String globalPicklistId = resolveGlobalPicklistId(field);
        if (globalPicklistId != null) {
            return picklistValueRepository
                    .findByPicklistSourceTypeAndPicklistSourceIdAndActiveTrueOrderBySortOrderAsc(
                            SOURCE_GLOBAL, globalPicklistId);
        }

        return picklistValueRepository
                .findByPicklistSourceTypeAndPicklistSourceIdAndActiveTrueOrderBySortOrderAsc(
                        SOURCE_FIELD, fieldId);
    }

    @Transactional
    public List<PicklistValue> setFieldPicklistValues(String fieldId, List<PicklistValueRequest> values) {
        log.info("Setting {} values for field picklist: {}", values.size(), fieldId);
        fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
        return replacePicklistValues(SOURCE_FIELD, fieldId, values);
    }

    // --- Dependency Management ---

    @Transactional(readOnly = true)
    public List<PicklistDependency> getDependencies(String fieldId) {
        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
        List<PicklistDependency> deps = picklistDependencyRepository.findByControllingField(field);
        deps.addAll(picklistDependencyRepository.findByDependentField(field));
        return deps;
    }

    @Transactional
    public PicklistDependency setDependency(String controllingFieldId, String dependentFieldId,
                                            Map<String, List<String>> mapping) {
        log.info("Setting dependency: controlling={}, dependent={}", controllingFieldId, dependentFieldId);

        Field controllingField = fieldRepository.findById(controllingFieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", controllingFieldId));
        Field dependentField = fieldRepository.findById(dependentFieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", dependentFieldId));

        // Validate both fields are picklist types
        validatePicklistType(controllingField, "Controlling field");
        validatePicklistType(dependentField, "Dependent field");

        // Validate mapping values exist
        validateDependencyMapping(controllingField, dependentField, mapping);

        String mappingJson;
        try {
            mappingJson = objectMapper.writeValueAsString(mapping);
        } catch (JsonProcessingException e) {
            throw new ValidationException("mapping", "Failed to serialize mapping: " + e.getMessage());
        }

        PicklistDependency dependency = picklistDependencyRepository
                .findByControllingFieldAndDependentField(controllingField, dependentField)
                .orElse(new PicklistDependency(controllingField, dependentField, mappingJson));
        dependency.setMapping(mappingJson);

        return picklistDependencyRepository.save(dependency);
    }

    @Transactional
    public void removeDependency(String controllingFieldId, String dependentFieldId) {
        log.info("Removing dependency: controlling={}, dependent={}", controllingFieldId, dependentFieldId);

        Field controllingField = fieldRepository.findById(controllingFieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", controllingFieldId));
        Field dependentField = fieldRepository.findById(dependentFieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", dependentFieldId));

        PicklistDependency dependency = picklistDependencyRepository
                .findByControllingFieldAndDependentField(controllingField, dependentField)
                .orElseThrow(() -> new ResourceNotFoundException("PicklistDependency",
                        controllingFieldId + " -> " + dependentFieldId));

        picklistDependencyRepository.delete(dependency);
    }

    // --- Value Validation (used by B7 runtime validation) ---

    @Transactional(readOnly = true)
    public boolean isValidPicklistValue(String fieldId, String value) {
        List<PicklistValue> values = getFieldPicklistValues(fieldId);
        return values.stream().anyMatch(v -> v.getValue().equals(value));
    }

    // --- Internal helpers ---

    private List<PicklistValue> replacePicklistValues(String sourceType, String sourceId,
                                                       List<PicklistValueRequest> values) {
        picklistValueRepository.deleteByPicklistSourceTypeAndPicklistSourceId(sourceType, sourceId);
        return savePicklistValues(sourceType, sourceId, values);
    }

    private List<PicklistValue> savePicklistValues(String sourceType, String sourceId,
                                                    List<PicklistValueRequest> values) {
        List<PicklistValue> entities = values.stream()
                .map(req -> {
                    PicklistValue pv = new PicklistValue(sourceType, sourceId, req.getValue(), req.getLabel());
                    pv.setDefault(req.isDefault());
                    pv.setActive(req.isActive());
                    pv.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
                    pv.setColor(req.getColor());
                    pv.setDescription(req.getDescription());
                    return pv;
                })
                .collect(Collectors.toList());
        return picklistValueRepository.saveAll(entities);
    }

    private String resolveGlobalPicklistId(Field field) {
        if (field.getFieldTypeConfig() == null || field.getFieldTypeConfig().isBlank()) {
            return null;
        }
        try {
            var config = objectMapper.readTree(field.getFieldTypeConfig());
            var node = config.get("globalPicklistId");
            return node != null && !node.isNull() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private void validatePicklistType(Field field, String label) {
        String type = field.getType().toUpperCase();
        if (!"PICKLIST".equals(type) && !"MULTI_PICKLIST".equals(type)) {
            throw new ValidationException("field",
                    label + " must be of type PICKLIST or MULTI_PICKLIST, got: " + field.getType());
        }
    }

    private void validateDependencyMapping(Field controllingField, Field dependentField,
                                           Map<String, List<String>> mapping) {
        Set<String> controllingValues = getFieldPicklistValues(controllingField.getId()).stream()
                .map(PicklistValue::getValue)
                .collect(Collectors.toSet());
        Set<String> dependentValues = getFieldPicklistValues(dependentField.getId()).stream()
                .map(PicklistValue::getValue)
                .collect(Collectors.toSet());

        for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
            if (!controllingValues.contains(entry.getKey())) {
                throw new ValidationException("mapping",
                        "Controlling value '" + entry.getKey() + "' not found in controlling field's picklist");
            }
            for (String depVal : entry.getValue()) {
                if (!dependentValues.contains(depVal)) {
                    throw new ValidationException("mapping",
                            "Dependent value '" + depVal + "' not found in dependent field's picklist");
                }
            }
        }
    }
}
