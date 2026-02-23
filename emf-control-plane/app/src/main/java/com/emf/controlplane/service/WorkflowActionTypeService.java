package com.emf.controlplane.service;

import com.emf.controlplane.dto.WorkflowActionTypeDto;
import com.emf.controlplane.entity.WorkflowActionType;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.WorkflowActionTypeRepository;
import com.emf.controlplane.service.workflow.ActionHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing workflow action types.
 * Provides read access to the action type registry and handler availability information.
 */
@Service
public class WorkflowActionTypeService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowActionTypeService.class);

    private final WorkflowActionTypeRepository repository;
    private final ActionHandlerRegistry handlerRegistry;

    public WorkflowActionTypeService(WorkflowActionTypeRepository repository,
                                      ActionHandlerRegistry handlerRegistry) {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * Lists all action types with handler availability status.
     */
    @Transactional(readOnly = true)
    public List<WorkflowActionTypeDto> listActionTypes() {
        return repository.findAllByOrderByNameAsc().stream()
            .map(entity -> WorkflowActionTypeDto.fromEntity(entity, handlerRegistry.hasHandler(entity.getKey())))
            .toList();
    }

    /**
     * Lists only active action types with handler availability status.
     */
    @Transactional(readOnly = true)
    public List<WorkflowActionTypeDto> listActiveActionTypes() {
        return repository.findByActiveTrue().stream()
            .map(entity -> WorkflowActionTypeDto.fromEntity(entity, handlerRegistry.hasHandler(entity.getKey())))
            .toList();
    }

    /**
     * Gets a single action type by its key.
     */
    @Transactional(readOnly = true)
    public WorkflowActionTypeDto getByKey(String key) {
        WorkflowActionType entity = repository.findByKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("WorkflowActionType", key));
        return WorkflowActionTypeDto.fromEntity(entity, handlerRegistry.hasHandler(entity.getKey()));
    }

    /**
     * Gets a single action type by its ID.
     */
    @Transactional(readOnly = true)
    public WorkflowActionTypeDto getById(String id) {
        WorkflowActionType entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WorkflowActionType", id));
        return WorkflowActionTypeDto.fromEntity(entity, handlerRegistry.hasHandler(entity.getKey()));
    }

    /**
     * Lists action types by category.
     */
    @Transactional(readOnly = true)
    public List<WorkflowActionTypeDto> listByCategory(String category) {
        return repository.findByCategoryAndActiveTrue(category).stream()
            .map(entity -> WorkflowActionTypeDto.fromEntity(entity, handlerRegistry.hasHandler(entity.getKey())))
            .toList();
    }

    /**
     * Toggles the active status of an action type.
     */
    @Transactional
    public WorkflowActionTypeDto toggleActive(String id) {
        WorkflowActionType entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WorkflowActionType", id));
        entity.setActive(!entity.isActive());
        log.info("Toggled action type '{}' active status to: {}", entity.getKey(), entity.isActive());
        entity = repository.save(entity);
        return WorkflowActionTypeDto.fromEntity(entity, handlerRegistry.hasHandler(entity.getKey()));
    }
}
