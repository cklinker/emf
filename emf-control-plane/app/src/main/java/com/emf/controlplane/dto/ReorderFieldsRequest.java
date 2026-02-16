package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request DTO for bulk-reordering fields in a collection.
 * Accepts an ordered list of field IDs; the position in the list
 * determines the new {@code field_order} value (0-based).
 */
public class ReorderFieldsRequest {

    @NotNull(message = "fieldIds must not be null")
    @NotEmpty(message = "fieldIds must not be empty")
    private List<String> fieldIds;

    public ReorderFieldsRequest() {
    }

    public ReorderFieldsRequest(List<String> fieldIds) {
        this.fieldIds = fieldIds;
    }

    public List<String> getFieldIds() {
        return fieldIds;
    }

    public void setFieldIds(List<String> fieldIds) {
        this.fieldIds = fieldIds;
    }
}
