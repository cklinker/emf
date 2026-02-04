package com.emf.controlplane.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing UI page.
 * All fields are optional - only provided fields will be updated.
 */
public class UpdateUiPageRequest {

    @Size(min = 1, max = 100, message = "Page name must be between 1 and 100 characters")
    private String name;

    @Size(min = 1, max = 200, message = "Page path must be between 1 and 200 characters")
    @Pattern(regexp = "^/.*", message = "Page path must start with /")
    private String path;

    @Size(max = 200, message = "Page title must not exceed 200 characters")
    private String title;

    private String config;

    private Boolean active;

    public UpdateUiPageRequest() {
    }

    public UpdateUiPageRequest(String name, String path, String title) {
        this.name = name;
        this.path = path;
        this.title = title;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "UpdateUiPageRequest{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", title='" + title + '\'' +
                ", active=" + active +
                '}';
    }
}
