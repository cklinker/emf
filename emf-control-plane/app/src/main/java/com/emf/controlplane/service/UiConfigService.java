package com.emf.controlplane.service;

import com.emf.controlplane.dto.CreateUiMenuRequest;
import com.emf.controlplane.dto.CreateUiPageRequest;
import com.emf.controlplane.dto.UiMenuItemRequest;
import com.emf.controlplane.dto.UpdateUiMenuRequest;
import com.emf.controlplane.dto.UpdateUiPageRequest;
import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiMenuItem;
import com.emf.controlplane.entity.UiPage;
import com.emf.controlplane.event.ConfigEventPublisher;
import com.emf.controlplane.exception.DuplicateResourceException;
import com.emf.controlplane.exception.ResourceNotFoundException;
import com.emf.controlplane.repository.UiMenuRepository;
import com.emf.controlplane.repository.UiPageRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing UI configuration including pages and menus.
 * Handles CRUD operations with validation and event publishing.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>5.1: Return bootstrap configuration including pages and menus</li>
 *   <li>5.2: Return list of UI pages</li>
 *   <li>5.3: Create UI page with valid data and return created page</li>
 *   <li>5.4: Update UI page and persist changes</li>
 *   <li>5.5: Return list of UI menus</li>
 *   <li>5.6: Update UI menu and persist changes</li>
 * </ul>
 */
@Service
public class UiConfigService {

    private static final Logger log = LoggerFactory.getLogger(UiConfigService.class);

    private final UiPageRepository pageRepository;
    private final UiMenuRepository menuRepository;
    private final ConfigEventPublisher eventPublisher;

    public UiConfigService(
            UiPageRepository pageRepository,
            UiMenuRepository menuRepository,
            @Nullable ConfigEventPublisher eventPublisher) {
        this.pageRepository = pageRepository;
        this.menuRepository = menuRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Gets the bootstrap configuration for the UI.
     * Returns all active pages, menus, theme settings, branding, features, and OIDC providers.
     *
     * @return BootstrapConfig containing pages, menus, theme, branding, features, and oidcProviders
     *
     * Validates: Requirement 5.1
     */
    @Transactional(readOnly = true)
    public BootstrapConfig getBootstrapConfig() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Getting bootstrap configuration for tenant: {}", tenantId);

        List<UiPage> pages;
        List<UiMenu> menus;
        if (tenantId != null) {
            pages = pageRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
            menus = menuRepository.findByTenantIdWithItemsOrderByNameAsc(tenantId);
        } else {
            pages = pageRepository.findByActiveTrueOrderByNameAsc();
            menus = menuRepository.findAllWithItemsOrderByNameAsc();
        }

        return new BootstrapConfig(pages, menus);
    }

    /**
     * Lists all active UI pages.
     * Returns pages ordered by name for consistent display.
     *
     * @return List of active UI pages
     *
     * Validates: Requirement 5.2
     */
    @Transactional(readOnly = true)
    public List<UiPage> listPages() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing all active UI pages for tenant: {}", tenantId);
        if (tenantId != null) {
            return pageRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);
        }
        return pageRepository.findByActiveTrueOrderByNameAsc();
    }

    /**
     * Creates a new UI page with the given configuration.
     * Validates that the path is unique before persisting.
     *
     * @param request The page creation request
     * @return The created UI page with generated ID
     * @throws DuplicateResourceException if a page with the same path already exists
     *
     * Validates: Requirement 5.3
     */
    @Transactional
    public UiPage createPage(CreateUiPageRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating UI page with name: {} for tenant: {}", request.getName(), tenantId);

        // Check for duplicate path
        if (tenantId != null) {
            if (pageRepository.existsByTenantIdAndPathAndActiveTrue(tenantId, request.getPath())) {
                throw new DuplicateResourceException("UiPage", "path", request.getPath());
            }
        } else {
            if (pageRepository.existsByPathAndActiveTrue(request.getPath())) {
                throw new DuplicateResourceException("UiPage", "path", request.getPath());
            }
        }

        // Create the page entity
        UiPage page = new UiPage(request.getName(), request.getPath());
        page.setTitle(request.getTitle());
        page.setConfig(request.getConfig());
        page.setActive(true);
        if (tenantId != null) {
            page.setTenantId(tenantId);
        }

        // Save the page
        page = pageRepository.save(page);

        // Publish event (stubbed for now - will be implemented in task 11)
        publishUiChangedEvent();

        log.info("Created UI page with id: {}", page.getId());
        return page;
    }

    /**
     * Updates an existing UI page.
     * Only provided fields will be updated.
     *
     * @param id The page ID to update
     * @param request The update request with new values
     * @return The updated UI page
     * @throws ResourceNotFoundException if the page does not exist or is inactive
     * @throws DuplicateResourceException if updating path to one that already exists
     *
     * Validates: Requirement 5.4
     */
    @Transactional
    public UiPage updatePage(String id, UpdateUiPageRequest request) {
        log.info("Updating UI page with id: {}", id);

        UiPage page = pageRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("UiPage", id));

        // Update name if provided
        if (request.getName() != null) {
            page.setName(request.getName());
        }

        // Update path if provided
        if (request.getPath() != null && !request.getPath().equals(page.getPath())) {
            if (pageRepository.existsByPathAndActiveTrue(request.getPath())) {
                throw new DuplicateResourceException("UiPage", "path", request.getPath());
            }
            page.setPath(request.getPath());
        }

        // Update title if provided
        if (request.getTitle() != null) {
            page.setTitle(request.getTitle());
        }

        // Update config if provided
        if (request.getConfig() != null) {
            page.setConfig(request.getConfig());
        }

        // Update active status if provided
        if (request.getActive() != null) {
            page.setActive(request.getActive());
        }

        // Save the updated page
        page = pageRepository.save(page);

        // Publish event
        publishUiChangedEvent();

        log.info("Updated UI page with id: {}", id);
        return page;
    }

    /**
     * Lists all UI menus.
     * Returns menus ordered by name for consistent display.
     *
     * @return List of all UI menus
     *
     * Validates: Requirement 5.5
     */
    @Transactional(readOnly = true)
    public List<UiMenu> listMenus() {
        String tenantId = TenantContextHolder.getTenantId();
        log.debug("Listing all UI menus for tenant: {}", tenantId);
        if (tenantId != null) {
            return menuRepository.findByTenantIdWithItemsOrderByNameAsc(tenantId);
        }
        return menuRepository.findAllByOrderByNameAsc();
    }

    /**
     * Creates a new UI menu with the given configuration.
     * Validates that the menu name is unique within the tenant before persisting.
     *
     * @param request The menu creation request
     * @return The created UI menu with generated ID
     * @throws DuplicateResourceException if a menu with the same name already exists for this tenant
     */
    @Transactional
    public UiMenu createMenu(CreateUiMenuRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Creating UI menu with name: {} for tenant: {}", request.getName(), tenantId);

        // Check for duplicate name within tenant
        if (tenantId != null) {
            if (menuRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                throw new DuplicateResourceException("UiMenu", "name", request.getName());
            }
        } else {
            if (menuRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("UiMenu", "name", request.getName());
            }
        }

        // Create the menu entity
        UiMenu menu = new UiMenu(request.getName());
        if (tenantId != null) {
            menu.setTenantId(tenantId);
        }
        if (request.getDescription() != null) {
            menu.setDescription(request.getDescription());
        }

        // Add items if provided
        if (request.getItems() != null) {
            for (int i = 0; i < request.getItems().size(); i++) {
                UiMenuItemRequest itemRequest = request.getItems().get(i);
                UiMenuItem item = new UiMenuItem(
                        itemRequest.getLabel(),
                        itemRequest.getPath(),
                        itemRequest.getDisplayOrder() != null ? itemRequest.getDisplayOrder() : i
                );
                if (itemRequest.getIcon() != null) {
                    item.setIcon(itemRequest.getIcon());
                }
                item.setActive(itemRequest.getActive() != null ? itemRequest.getActive() : true);
                menu.addItem(item);
            }
        }

        // Save the menu
        menu = menuRepository.save(menu);

        // Publish event
        publishUiChangedEvent();

        log.info("Created UI menu with id: {}", menu.getId());
        return menu;
    }

    /**
     * Updates an existing UI menu.
     * Only provided fields will be updated.
     * If items are provided, they will replace the existing items.
     *
     * @param id The menu ID to update
     * @param request The update request with new values
     * @return The updated UI menu
     * @throws ResourceNotFoundException if the menu does not exist
     * @throws DuplicateResourceException if updating name to one that already exists
     *
     * Validates: Requirement 5.6
     */
    @Transactional
    public UiMenu updateMenu(String id, UpdateUiMenuRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        log.info("Updating UI menu with id: {} for tenant: {}", id, tenantId);

        UiMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UiMenu", id));

        // Update name if provided
        if (request.getName() != null && !request.getName().equals(menu.getName())) {
            if (tenantId != null) {
                if (menuRepository.existsByTenantIdAndName(tenantId, request.getName())) {
                    throw new DuplicateResourceException("UiMenu", "name", request.getName());
                }
            } else {
                if (menuRepository.existsByName(request.getName())) {
                    throw new DuplicateResourceException("UiMenu", "name", request.getName());
                }
            }
            menu.setName(request.getName());
        }

        // Set tenantId if not already set
        if (tenantId != null && menu.getTenantId() == null) {
            menu.setTenantId(tenantId);
        }

        // Update description if provided
        if (request.getDescription() != null) {
            menu.setDescription(request.getDescription());
        }

        // Update items if provided
        if (request.getItems() != null) {
            updateMenuItems(menu, request.getItems());
        }

        // Save the updated menu
        menu = menuRepository.save(menu);

        // Publish event
        publishUiChangedEvent();

        log.info("Updated UI menu with id: {}", id);
        return menu;
    }

    /**
     * Deletes a UI menu by its ID.
     * Cascade deletes all associated menu items.
     *
     * @param id The menu ID to delete
     * @throws ResourceNotFoundException if the menu does not exist
     */
    @Transactional
    public void deleteMenu(String id) {
        log.info("Deleting UI menu with id: {}", id);

        UiMenu menu = menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UiMenu", id));

        menuRepository.delete(menu);

        // Publish event
        publishUiChangedEvent();

        log.info("Deleted UI menu with id: {}", id);
    }

    /**
     * Updates the menu items for a menu.
     * Items with IDs will be updated, items without IDs will be created.
     * Items not in the request will be marked as inactive.
     *
     * @param menu The menu to update items for
     * @param itemRequests The list of item requests
     */
    private void updateMenuItems(UiMenu menu, List<UiMenuItemRequest> itemRequests) {
        // Create a map of existing items by ID for quick lookup
        Map<String, UiMenuItem> existingItems = new HashMap<>();
        for (UiMenuItem item : menu.getItems()) {
            existingItems.put(item.getId(), item);
        }

        // Track which items are in the request
        List<String> requestedItemIds = new ArrayList<>();

        // Process each item request
        for (UiMenuItemRequest itemRequest : itemRequests) {
            if (itemRequest.getId() != null && existingItems.containsKey(itemRequest.getId())) {
                // Update existing item
                UiMenuItem existingItem = existingItems.get(itemRequest.getId());
                updateMenuItem(existingItem, itemRequest);
                requestedItemIds.add(itemRequest.getId());
            } else {
                // Create new item
                UiMenuItem newItem = new UiMenuItem(
                        itemRequest.getLabel(),
                        itemRequest.getPath(),
                        itemRequest.getDisplayOrder() != null ? itemRequest.getDisplayOrder() : 0
                );
                if (itemRequest.getIcon() != null) {
                    newItem.setIcon(itemRequest.getIcon());
                }
                newItem.setActive(itemRequest.getActive() != null ? itemRequest.getActive() : true);
                menu.addItem(newItem);
                requestedItemIds.add(newItem.getId());
            }
        }

        // Mark items not in the request as inactive
        for (UiMenuItem item : menu.getItems()) {
            if (!requestedItemIds.contains(item.getId())) {
                item.setActive(false);
            }
        }
    }

    /**
     * Updates a menu item with values from the request.
     *
     * @param item The item to update
     * @param request The request with new values
     */
    private void updateMenuItem(UiMenuItem item, UiMenuItemRequest request) {
        if (request.getLabel() != null) {
            item.setLabel(request.getLabel());
        }
        if (request.getPath() != null) {
            item.setPath(request.getPath());
        }
        if (request.getIcon() != null) {
            item.setIcon(request.getIcon());
        }
        if (request.getDisplayOrder() != null) {
            item.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }
    }

    /**
     * Retrieves a UI page by its ID.
     * Only returns active pages.
     *
     * @param id The page ID
     * @return The page if found
     * @throws ResourceNotFoundException if the page does not exist or is inactive
     */
    @Transactional(readOnly = true)
    public UiPage getPage(String id) {
        log.debug("Getting UI page with id: {}", id);
        return pageRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("UiPage", id));
    }

    /**
     * Retrieves a UI menu by its ID.
     *
     * @param id The menu ID
     * @return The menu if found
     * @throws ResourceNotFoundException if the menu does not exist
     */
    @Transactional(readOnly = true)
    public UiMenu getMenu(String id) {
        log.debug("Getting UI menu with id: {}", id);
        return menuRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UiMenu", id));
    }

    /**
     * Publishes a UI configuration changed event to Kafka.
     * Only publishes if the event publisher is available (Kafka is enabled).
     *
     * Validates: Requirement 5.7
     */
    private void publishUiChangedEvent() {
        if (eventPublisher != null) {
            List<UiPage> pages = pageRepository.findByActiveTrueOrderByNameAsc();
            List<UiMenu> menus = menuRepository.findAllByOrderByNameAsc();
            eventPublisher.publishUiChanged(pages, menus);
        } else {
            log.debug("Event publishing disabled - UI configuration changed");
        }
    }

    /**
     * Inner class to hold bootstrap configuration data.
     */
    public static class BootstrapConfig {
        private final List<UiPage> pages;
        private final List<UiMenu> menus;

        public BootstrapConfig(List<UiPage> pages, List<UiMenu> menus) {
            this.pages = pages;
            this.menus = menus;
        }

        public List<UiPage> getPages() {
            return pages;
        }

        public List<UiMenu> getMenus() {
            return menus;
        }
    }
}
