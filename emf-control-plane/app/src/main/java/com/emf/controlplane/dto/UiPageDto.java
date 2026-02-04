package com.emf.controlplane.dto;

import com.emf.controlplane.entity.UiPage;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for UI Page API responses.
 * Provides a clean API representation of a UiPage entity.
 */
public class UiPageDto {

    private String id;
    private String name;
    private String path;
    private String title;
    private String config;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public UiPageDto() {
    }

    public UiPageDto(String id, String name, String path, String title, String config,
                     boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.title = title;
        this.config = config;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a UiPageDto from a UiPage entity.
     *
     * @param page The UI page entity to convert
     * @return A new UiPageDto with data from the entity
     */
    public static UiPageDto fromEntity(UiPage page) {
        if (page == null) {
            return null;
        }
        return new UiPageDto(
                page.getId(),
                page.getName(),
                page.getPath(),
                page.getTitle(),
                page.getConfig(),
                page.isActive(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "UiPageDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", title='" + title + '\'' +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UiPageDto that = (UiPageDto) o;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(path, that.path) &&
                Objects.equals(title, that.title) &&
                Objects.equals(config, that.config) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, path, title, config, active, createdAt, updatedAt);
    }
}
