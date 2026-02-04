package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Role;

import java.time.Instant;
import java.util.Objects;

/**
 * Response DTO for Role API responses.
 * Provides a clean API representation of a Role entity.
 */
public class RoleDto {

    private String id;
    private String name;
    private String description;
    private Instant createdAt;

    public RoleDto() {
    }

    public RoleDto(String id, String name, String description, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    /**
     * Creates a RoleDto from a Role entity.
     *
     * @param role The role entity to convert
     * @return A new RoleDto with data from the entity
     */
    public static RoleDto fromEntity(Role role) {
        if (role == null) {
            return null;
        }
        return new RoleDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getCreatedAt()
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "RoleDto{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleDto roleDto = (RoleDto) o;
        return Objects.equals(id, roleDto.id) &&
                Objects.equals(name, roleDto.name) &&
                Objects.equals(description, roleDto.description) &&
                Objects.equals(createdAt, roleDto.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, createdAt);
    }
}
