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
 * After-save hook for the navigation config collections ({@code ui-menus} and
 * {@code ui-menu-items} — one instance registered per collection). Publishes a
 * menu-changed event on create, update, and delete so every pod evicts its
 * {@link io.kelta.runtime.router.SystemCollectionCache} entries for the menu
 * collections (apps/nav v2 makes menu shape load-bearing — before this the write
 * evicted only the serving pod's cache and the fleet relied on the Caffeine TTL).
 *
 * <p>Subject: {@code kelta.config.menu.changed.<tenantId>} — tenant-scoped, since the
 * consumer eviction is per tenant + collection, not per record. Mirrors
 * {@link UIPageConfigEventPublisher}; consumed by {@link MenuCacheInvalidationListener}.
 */
public class MenuConfigEventPublisher implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(MenuConfigEventPublisher.class);

    static final String SUBJECT_PREFIX = "kelta.config.menu.changed.";
    static final String EVENT_TYPE = "kelta.config.menu.changed";

    private final PlatformEventPublisher eventPublisher;
    private final String collectionName;

    public MenuConfigEventPublisher(PlatformEventPublisher eventPublisher, String collectionName) {
        this.eventPublisher = eventPublisher;
        this.collectionName = collectionName;
    }

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public void afterCreate(Map<String, Object> record, String tenantId) {
        Object id = record.get("id");
        Object name = record.get("name");
        publish(id != null ? id.toString() : null,
                name != null ? name.toString() : null, tenantId, ChangeType.CREATED);
    }

    @Override
    public void afterUpdate(String id, Map<String, Object> record,
                            Map<String, Object> previous, String tenantId) {
        Object name = record.get("name");
        publish(id, name != null ? name.toString() : null, tenantId, ChangeType.UPDATED);
    }

    @Override
    public void afterDelete(String id, String tenantId) {
        publish(id, null, tenantId, ChangeType.DELETED);
    }

    private void publish(String recordId, String name, String tenantId, ChangeType changeType) {
        if (tenantId == null) {
            log.warn("Menu {} event for '{}' missing tenantId, cannot broadcast", changeType, collectionName);
            return;
        }

        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(recordId);
        payload.setName(name);
        payload.setChangeType(changeType);

        PlatformEvent<CollectionChangedPayload> event = EventFactory.createEvent(EVENT_TYPE, payload);
        event.setTenantId(tenantId);
        String subject = SUBJECT_PREFIX + tenantId;
        log.info("Publishing menu {} event for '{}' (id={}, tenant={})",
                changeType, collectionName, recordId, tenantId);
        eventPublisher.publish(subject, event);
    }
}
