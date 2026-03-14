package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Seeds Cerbos policies on application startup.
 *
 * <p>Ensures all tenant policies are synced to Cerbos when the worker starts.
 */
@Component
public class CerbosPolicySeeder {

    private static final Logger log = LoggerFactory.getLogger(CerbosPolicySeeder.class);

    private final CerbosPolicySyncService syncService;

    public CerbosPolicySeeder(CerbosPolicySyncService syncService) {
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedPolicies() {
        log.info("Seeding Cerbos policies for all tenants on startup");
        try {
            // Seed base (ancestor) policies first — Cerbos requires these
            // before scoped per-tenant policies can compile
            syncService.seedBasePolicies();
            syncService.syncAllTenants();
            log.info("Cerbos policy seeding complete");
        } catch (Exception e) {
            log.error("Failed to seed Cerbos policies on startup: {}", e.getMessage(), e);
        }
    }
}
