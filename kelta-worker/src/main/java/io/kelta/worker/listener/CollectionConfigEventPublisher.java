package io.kelta.worker.listener;

import io.kelta.runtime.event.ChangeType;
import io.kelta.runtime.event.CollectionChangedPayload;
import io.kelta.runtime.event.EventFactory;
import io.kelta.runtime.event.PlatformEvent;
import io.kelta.runtime.event.PlatformEventPublisher;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Before-save hook for the "collections" system collection that publishes
 * config change events after collection create/update/delete.
 *
 * <p>This hook bridges the gap between the CRUD API (which creates/updates
 * collection metadata in the database) and the event-based notification system
 * that triggers schema changes on workers and route updates on the gateway.
 *
 * <p>Events are published to {@code kelta.config.collection.changed.*} subjects,
 * consumed by:
 * <ul>
 *   <li>{@link CollectionSchemaListener} — triggers table creation/migration on workers</li>
 *   <li>Gateway ConfigEventListener — updates route registry</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class CollectionConfigEventPublisher implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(CollectionConfigEventPublisher.class);

    static final String SUBJECT_PREFIX = "kelta.config.collection.changed.";

    private final PlatformEventPublisher eventPublisher;

    public CollectionConfigEventPublisher(PlatformEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getCollectionName() {
        return "collections";
    }

    @Override
    public int getOrder() {
        // Run after the SchemaLifecycleModule's CollectionLifecycleHook (order 0)
        return 100;
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
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(id);
        payload.setChangeType(ChangeType.DELETED);
        sendEvent(payload, tenantId);
    }

    private void publishEvent(Map<String, Object> record, ChangeType changeType, String tenantId) {
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(getString(record, "id"));
        payload.setName(getString(record, "name"));
        payload.setDisplayName(getString(record, "displayName"));
        payload.setDescription(getString(record, "description"));
        payload.setChangeType(changeType);

        if (payload.getName() == null) {
            log.warn("Skipping collection changed event: record missing 'name' field (id={})", payload.getId());
            return;
        }

        Object active = record.get("active");
        if (active instanceof Boolean b) {
            payload.setActive(b);
        } else {
            payload.setActive(true);
        }

        Object version = record.get("currentVersion");
        if (version instanceof Number n) {
            payload.setCurrentVersion(n.intValue());
        }

        sendEvent(payload, tenantId);
    }

    private void sendEvent(CollectionChangedPayload payload, String tenantId) {
        PlatformEvent<CollectionChangedPayload> event =
                EventFactory.createEvent("kelta.config.collection.changed", payload);
        event.setTenantId(tenantId);
        String subject = SUBJECT_PREFIX + payload.getId();
        log.info("Publishing collection {} event for '{}' (id={}) to '{}'",
                payload.getChangeType(), payload.getName(), payload.getId(), subject);
        eventPublisher.publish(subject, event);
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
