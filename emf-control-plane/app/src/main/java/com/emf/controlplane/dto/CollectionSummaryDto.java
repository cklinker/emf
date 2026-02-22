package com.emf.controlplane.dto;

import com.emf.controlplane.entity.Collection;

/**
 * Lightweight DTO for collection metadata.
 * Returns only id, name, and displayName — used by UI components that
 * need collection references (sidebar, dropdowns, ID→name maps) without
 * the overhead of full CollectionDto with fields and timestamps.
 */
public record CollectionSummaryDto(String id, String name, String displayName) {

    public static CollectionSummaryDto fromEntity(Collection collection) {
        return new CollectionSummaryDto(
                collection.getId(),
                collection.getName(),
                collection.getDisplayName()
        );
    }
}
