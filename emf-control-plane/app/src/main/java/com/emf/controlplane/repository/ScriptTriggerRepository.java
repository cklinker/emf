package com.emf.controlplane.repository;

import com.emf.controlplane.entity.ScriptTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScriptTriggerRepository extends JpaRepository<ScriptTrigger, String> {

    List<ScriptTrigger> findByCollectionIdAndTriggerEventAndActiveTrueOrderByExecutionOrderAsc(
            String collectionId, String triggerEvent);
}
