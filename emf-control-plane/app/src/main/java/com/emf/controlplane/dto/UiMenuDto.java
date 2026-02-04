package com.emf.controlplane.dto;

import com.emf.controlplane.entity.UiMenu;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Response DTO for UI Menu API responses.
 * Provides a clean API representation of a UiMenu entity.
 */
public class UiMenuDto {

    private String id;
    private String name;
    private String description;
    private List<UiMenuItemDto> items;
    private Instant createdAt;
    private Instant updatedAt;

    public UiMenuDto() {
    }

    public UiMenuDto(String id, String name, String description, List<UiMenuItemDto> items,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.items = items;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a UiMenuDto from a UiMenu entity.
     *
     * @param menu The UI menu entity to convert
     * @return A new UiMenuDto with data from the entity
     */
    public static UiMenuDto fromEntity(UiMenu menu) {
        if (menu == null) {
            return null;
        }
        List<UiMenuItemDto> itemDtos = menu.getItems() != null
                ? menu.getItems().stream()
                    .filter(item -> item.isActive())
                    .map(UiMenuItemDto::fromEntity)
                    .collect(Collectors.toList())
                : List.of();

        return new UiMenuDto(
                menu.getId(),
                menu.getName(),
                menu.getDescription(),
                itemDtos,
                menu.getCreatedAt(),
                menu.getUpdatedAt()
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<UiMenuItemDto> getItems() {
        return items;
    }

    public void setItems(List<UiMenuItemDto> items) {
        this.items = items;
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
        return "UiMenuDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", items=" + items +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UiMenuDto that = (UiMenuDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(items, that.items) &&
                Objects.equals(createdAt, that.createdAt) &&
                Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, items, createdAt, updatedAt);
    }
}
