package com.emf.controlplane.service;

import com.emf.controlplane.audit.SetupAudited;
import com.emf.controlplane.dto.CreateScriptRequest;
import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Script;
import com.emf.controlplane.entity.ScriptExecutionLog;
import com.emf.controlplane.entity.ScriptTrigger;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.ScriptExecutionLogRepository;
import com.emf.controlplane.repository.ScriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScriptService {

    private static final Logger log = LoggerFactory.getLogger(ScriptService.class);

    private final ScriptRepository scriptRepository;
    private final ScriptExecutionLogRepository logRepository;
    private final CollectionService collectionService;

    public ScriptService(ScriptRepository scriptRepository,
                         ScriptExecutionLogRepository logRepository,
                         CollectionService collectionService) {
        this.scriptRepository = scriptRepository;
        this.logRepository = logRepository;
        this.collectionService = collectionService;
    }

    @Transactional(readOnly = true)
    public List<Script> listScripts(String tenantId) {
        return scriptRepository.findByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public Script getScript(String id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Script", id));
    }

    @Transactional
    @SetupAudited(section = "Scripts", entityType = "Script")
    public Script createScript(String tenantId, String userId, CreateScriptRequest request) {
        log.info("Creating script '{}' for tenant: {}", request.getName(), tenantId);

        Script script = new Script();
        script.setTenantId(tenantId);
        script.setName(request.getName());
        script.setDescription(request.getDescription());
        script.setScriptType(request.getScriptType());
        script.setLanguage(request.getLanguage() != null ? request.getLanguage() : "javascript");
        script.setSourceCode(request.getSourceCode());
        script.setActive(request.getActive() != null ? request.getActive() : true);
        script.setCreatedBy(userId);

        if (request.getTriggers() != null) {
            for (CreateScriptRequest.TriggerRequest triggerReq : request.getTriggers()) {
                ScriptTrigger trigger = new ScriptTrigger();
                trigger.setScript(script);
                trigger.setTriggerEvent(triggerReq.getTriggerEvent());
                trigger.setExecutionOrder(triggerReq.getExecutionOrder() != null ? triggerReq.getExecutionOrder() : 0);
                trigger.setActive(triggerReq.getActive() != null ? triggerReq.getActive() : true);

                if (triggerReq.getCollectionId() != null) {
                    Collection collection = collectionService.getCollection(triggerReq.getCollectionId());
                    trigger.setCollection(collection);
                }

                script.getTriggers().add(trigger);
            }
        }

        return scriptRepository.save(script);
    }

    @Transactional
    @SetupAudited(section = "Scripts", entityType = "Script")
    public Script updateScript(String id, CreateScriptRequest request) {
        log.info("Updating script: {}", id);
        Script script = getScript(id);

        if (request.getName() != null) script.setName(request.getName());
        if (request.getDescription() != null) script.setDescription(request.getDescription());
        if (request.getScriptType() != null) script.setScriptType(request.getScriptType());
        if (request.getLanguage() != null) script.setLanguage(request.getLanguage());
        if (request.getSourceCode() != null) script.setSourceCode(request.getSourceCode());
        if (request.getActive() != null) script.setActive(request.getActive());

        if (request.getTriggers() != null) {
            script.getTriggers().clear();
            for (CreateScriptRequest.TriggerRequest triggerReq : request.getTriggers()) {
                ScriptTrigger trigger = new ScriptTrigger();
                trigger.setScript(script);
                trigger.setTriggerEvent(triggerReq.getTriggerEvent());
                trigger.setExecutionOrder(triggerReq.getExecutionOrder() != null ? triggerReq.getExecutionOrder() : 0);
                trigger.setActive(triggerReq.getActive() != null ? triggerReq.getActive() : true);

                if (triggerReq.getCollectionId() != null) {
                    Collection collection = collectionService.getCollection(triggerReq.getCollectionId());
                    trigger.setCollection(collection);
                }

                script.getTriggers().add(trigger);
            }
        }

        return scriptRepository.save(script);
    }

    @Transactional
    @SetupAudited(section = "Scripts", entityType = "Script")
    public void deleteScript(String id) {
        log.info("Deleting script: {}", id);
        Script script = getScript(id);
        scriptRepository.delete(script);
    }

    // --- Execution Logs ---

    @Transactional(readOnly = true)
    public List<ScriptExecutionLog> listExecutionLogs(String tenantId) {
        return logRepository.findByTenantIdOrderByExecutedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<ScriptExecutionLog> listExecutionLogsByScript(String scriptId) {
        return logRepository.findByScriptIdOrderByExecutedAtDesc(scriptId);
    }
}
