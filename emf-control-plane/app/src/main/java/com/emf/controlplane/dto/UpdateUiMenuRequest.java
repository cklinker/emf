package com.emf.controlplane.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for updating an existing UI menu.
 * All fields are optional - only provided fields will be updated.
 */
public class UpdateUiMenuRequest {

    @Size(min = 1, max = 100, message = "Menu name must be between 1 and 100 characters")
    private String name;

    @Size(max = 500, message = "Menu description must not exceed 500 characters")
    private String description;

    @Valid
    private List<UiMenuItemRequest> items;

    public UpdateUiMenuRequest() {
    }

    public UpdateUiMenuRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public UpdateUiMenuRequest(String name, String description, List<UiMenuItemRequest> items) {
        this.name = name;
        this.description = description;
        this.items = items;
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

    public List<UiMenuItemRequest> getItems() {
        return items;
    }

    public void setItems(List<UiMenuItemRequest> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "UpdateUiMenuRequest{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", items=" + items +
                '}';
    }
}
