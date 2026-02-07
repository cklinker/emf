package com.emf.controlplane.tenant;

import com.emf.controlplane.entity.*;
import com.emf.controlplane.repository.UiMenuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages per-tenant provisioning.
 * Phase 1 uses a simplified approach: all data lives in the public schema,
 * isolated by tenant_id FK filtering. This manager seeds default data for new tenants.
 */
@Component
public class TenantSchemaManager {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaManager.class);

    private final UiMenuRepository uiMenuRepository;

    public TenantSchemaManager(UiMenuRepository uiMenuRepository) {
        this.uiMenuRepository = uiMenuRepository;
    }

    /**
     * Provisions default data for a new tenant.
     * Called during tenant creation after the tenant record is saved.
     *
     * @param tenantId The tenant ID
     * @param tenantSlug The tenant slug
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void provisionTenant(String tenantId, String tenantSlug) {
        log.info("Provisioning default data for tenant: {} ({})", tenantSlug, tenantId);

        seedDefaultMenus(tenantId);

        log.info("Provisioning complete for tenant: {} ({})", tenantSlug, tenantId);
    }

    /**
     * Seeds default navigation menus for a new tenant.
     */
    private void seedDefaultMenus(String tenantId) {
        UiMenu mainMenu = new UiMenu("main");
        mainMenu.setTenantId(tenantId);
        mainMenu.setDescription("Main navigation menu");

        UiMenuItem dashboardItem = new UiMenuItem();
        dashboardItem.setMenu(mainMenu);
        dashboardItem.setLabel("Dashboard");
        dashboardItem.setPath("/dashboard");
        dashboardItem.setIcon("LayoutDashboard");
        dashboardItem.setDisplayOrder(0);
        mainMenu.addItem(dashboardItem);

        UiMenuItem collectionsItem = new UiMenuItem();
        collectionsItem.setMenu(mainMenu);
        collectionsItem.setLabel("Collections");
        collectionsItem.setPath("/collections");
        collectionsItem.setIcon("Database");
        collectionsItem.setDisplayOrder(1);
        mainMenu.addItem(collectionsItem);

        UiMenuItem servicesItem = new UiMenuItem();
        servicesItem.setMenu(mainMenu);
        servicesItem.setLabel("Services");
        servicesItem.setPath("/services");
        servicesItem.setIcon("Server");
        servicesItem.setDisplayOrder(2);
        mainMenu.addItem(servicesItem);

        uiMenuRepository.save(mainMenu);
        log.debug("Seeded default menus for tenant: {}", tenantId);
    }

    /**
     * Checks if a tenant has been provisioned (has default data).
     *
     * @param tenantId The tenant ID
     * @return true if the tenant has been provisioned
     */
    public boolean isProvisioned(String tenantId) {
        return uiMenuRepository.existsByTenantIdAndName(tenantId, "main");
    }
}
