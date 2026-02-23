package com.emf.controlplane.repository;

import com.emf.controlplane.entity.WorkflowActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowActionTypeRepository extends JpaRepository<WorkflowActionType, String> {

    Optional<WorkflowActionType> findByKey(String key);

    List<WorkflowActionType> findByActiveTrue();

    List<WorkflowActionType> findByCategory(String category);

    List<WorkflowActionType> findByCategoryAndActiveTrue(String category);

    List<WorkflowActionType> findAllByOrderByNameAsc();

    boolean existsByKey(String key);
}
