package com.emf.controlplane.event;

import com.emf.controlplane.entity.UiMenu;
import com.emf.controlplane.entity.UiMenuItem;
import com.emf.controlplane.entity.UiPage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Payload for UI configuration changed events.
 * Contains the full UI configuration including pages and menus.
 *
 * <p>Requirements satisfied:
 * <ul>
 *   <li>10.3: Publish UI configuration change events to Kafka</li>
 * </ul>
 */
public class UiChangedPayload {

    private List<UiPagePayload> pages;
    private List<UiMenuPayload> menus;
    private Instant timestamp;

    /**
     * Default constructor for deserialization.
     */
    public UiChangedPayload() {
    }

    /**
     * Creates a payload with the full UI configuration.
     *
     * @param pages The list of UI pages
     * @param menus The list of UI menus
     * @return The payload with full UI configuration
     */
    public static UiChangedPayload create(List<UiPage> pages, List<UiMenu> menus) {
        UiChangedPayload payload = new UiChangedPayload();
        payload.setTimestamp(Instant.now());

        if (pages != null) {
            payload.setPages(pages.stream()
                    .map(UiPagePayload::fromEntity)
                    .collect(Collectors.toList()));
        }

        if (menus != null) {
            payload.setMenus(menus.stream()
                    .map(UiMenuPayload::fromEntity)
                    .collect(Collectors.toList()));
        }

        return payload;
    }

    public List<UiPagePayload> getPages() {
        return pages;
    }

    public void setPages(List<UiPagePayload> pages) {
        this.pages = pages;
    }

    public List<UiMenuPayload> getMenus() {
        return menus;
    }

    public void setMenus(List<UiMenuPayload> menus) {
        this.menus = menus;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UiChangedPayload that = (UiChangedPayload) o;
        return Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }

    @Override
    public String toString() {
        return "UiChangedPayload{" +
                "pages=" + (pages != null ? pages.size() : 0) +
                ", menus=" + (menus != null ? menus.size() : 0) +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * Nested class for UI page data in the payload.
     */
    public static class UiPagePayload {
        private String id;
        private String name;
        private String path;
        private String title;
        private String config;
        private boolean active;

        public UiPagePayload() {
        }

        public static UiPagePayload fromEntity(UiPage page) {
            UiPagePayload payload = new UiPagePayload();
            payload.setId(page.getId());
            payload.setName(page.getName());
            payload.setPath(page.getPath());
            payload.setTitle(page.getTitle());
            payload.setConfig(page.getConfig());
            payload.setActive(page.isActive());
            return payload;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getConfig() {
            return config;
        }

        public void setConfig(String config) {
            this.config = config;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }

    /**
     * Nested class for UI menu data in the payload.
     */
    public static class UiMenuPayload {
        private String id;
        private String name;
        private String description;
        private List<UiMenuItemPayload> items;

        public UiMenuPayload() {
        }

        public static UiMenuPayload fromEntity(UiMenu menu) {
            UiMenuPayload payload = new UiMenuPayload();
            payload.setId(menu.getId());
            payload.setName(menu.getName());
            payload.setDescription(menu.getDescription());
            if (menu.getItems() != null) {
                payload.setItems(menu.getItems().stream()
                        .filter(UiMenuItem::isActive)
                        .map(UiMenuItemPayload::fromEntity)
                        .collect(Collectors.toList()));
            }
            return payload;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<UiMenuItemPayload> getItems() {
            return items;
        }

        public void setItems(List<UiMenuItemPayload> items) {
            this.items = items;
        }
    }

    /**
     * Nested class for UI menu item data in the payload.
     */
    public static class UiMenuItemPayload {
        private String id;
        private String label;
        private String path;
        private String icon;
        private Integer displayOrder;

        public UiMenuItemPayload() {
        }

        public static UiMenuItemPayload fromEntity(UiMenuItem item) {
            UiMenuItemPayload payload = new UiMenuItemPayload();
            payload.setId(item.getId());
            payload.setLabel(item.getLabel());
            payload.setPath(item.getPath());
            payload.setIcon(item.getIcon());
            payload.setDisplayOrder(item.getDisplayOrder());
            return payload;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
        }
    }
}
