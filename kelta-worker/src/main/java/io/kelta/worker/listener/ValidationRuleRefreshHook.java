package io.kelta.worker.listener;

import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.worker.service.CollectionLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * After-save hook for the "validation-rules" system collection that refreshes
 * the in-memory {@link io.kelta.runtime.validation.ValidationRuleRegistry}
 * whenever a validation rule is created, updated, or deleted.
 *
 * <p>Without this hook, changes to validation rules (create, activate,
 * deactivate, delete) would only take effect after a full application restart
 * or collection schema refresh.
 *
 * @since 1.0.0
 */
public class ValidationRuleRefreshHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(ValidationRuleRefreshHook.class);

    private final CollectionLifecycleManager lifecycleManager;

    public ValidationRuleRefreshHook(CollectionLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }

    @Override
    public String getCollectionName() {
        return "validation-rules";
    }

    @Override
    public int getOrder() {
        // Run after the audit hook
        return 200;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        refreshRulesForCollection(record);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        refreshRulesForCollection(record);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        // On delete we don't have the collectionId easily, so refresh all active collections
        log.info("Validation rule deleted (id={}), refreshing all active collection rules", id);
        lifecycleManager.refreshAllValidationRules();
    }

    private void refreshRulesForCollection(Map<String, Object> record) {
        String collectionId = (String) record.get("collectionId");
        if (collectionId != null) {
            log.info("Validation rule changed for collection '{}', refreshing rules", collectionId);
            lifecycleManager.refreshValidationRules(collectionId);
        }
    }
}
