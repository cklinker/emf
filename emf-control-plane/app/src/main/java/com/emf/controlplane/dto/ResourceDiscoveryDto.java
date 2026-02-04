package com.emf.controlplane.dto;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for the resource discovery endpoint.
 * Contains metadata about all active collections including their schemas,
 * available operations, and authorization hints.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.1: Return all active collections with their schemas</li>
 *   <li>8.2: Include field definitions, types, and constraints</li>
 *   <li>8.3: Include available operations</li>
 *   <li>8.4: Include authorization hints</li>
 * </ul>
 */
public class ResourceDiscoveryDto {

    private List<ResourceMetadataDto> resources;

    public ResourceDiscoveryDto() {
    }

    public ResourceDiscoveryDto(List<ResourceMetadataDto> resources) {
        this.resources = resources;
    }

    public List<ResourceMetadataDto> getResources() {
        return resources;
    }

    public void setResources(List<ResourceMetadataDto> resources) {
        this.resources = resources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceDiscoveryDto that = (ResourceDiscoveryDto) o;
        return Objects.equals(resources, that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources);
    }

    @Override
    public String toString() {
        return "ResourceDiscoveryDto{" +
                "resources=" + resources +
                '}';
    }
}
