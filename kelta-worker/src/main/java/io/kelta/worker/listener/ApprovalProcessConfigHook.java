package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.workflow.BeforeSaveHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * After-save hook for the "approval-processes" system collection that broadcasts
 * a collection-changed event whenever an approval process is created,
 * updated, or deleted.
 *
 * <p>This ensures all worker pods refresh their knowledge of available approval
 * processes when configuration changes are made. The event is consumed by
 * {@link CollectionSchemaListener} on every pod.
 *
 * @since 1.0.0
 */
public class ApprovalProcessConfigHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(ApprovalProcessConfigHook.class);

    private final PlatformEventPublisher eventPublisher;

    public ApprovalProcessConfigHook(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getCollectionName() {
        return "approval-processes";
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        publishEvent(record, ChangeType.CREATED, tenantId);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                             Map<String, Object> previous, String tenantId) {
        publishEvent(record, ChangeType.UPDATED, tenantId);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        log.info("Approval process deleted (id={}), broadcasting refresh", id);
    }

    private void publishEvent(Map<String, Object> record, ChangeType changeType, String tenantId) {
        String rawCollectionId = (String) record.get("collectionId");
        if (rawCollectionId == null) {
            rawCollectionId = (String) record.get("collection_id");
        }
        if (rawCollectionId == null) {
            log.warn("Approval process record missing collectionId, cannot broadcast refresh");
            return;
        }

        final String collectionId = rawCollectionId;

        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(collectionId);
        payload.setName("approval-processes");
        payload.setChangeType(changeType);

        PlatformEvent<CollectionChangedPayload> event =
                EventFactory.createEvent("kelta.config.collection.changed", payload);
        event.setTenantId(tenantId);
        String subject = CollectionConfigEventPublisher.SUBJECT_PREFIX + collectionId;
        log.info("Publishing approval process config change event (collectionId={})", collectionId);
        eventPublisher.publish(subject, event);
    }
}
