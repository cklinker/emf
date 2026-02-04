package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new UI page.
 */
public class CreateUiPageRequest {

    @NotBlank(message = "Page name is required")
    @Size(min = 1, max = 100, message = "Page name must be between 1 and 100 characters")
    private String name;

    @NotBlank(message = "Page path is required")
    @Size(min = 1, max = 200, message = "Page path must be between 1 and 200 characters")
    @Pattern(regexp = "^/.*", message = "Page path must start with /")
    private String path;

    @Size(max = 200, message = "Page title must not exceed 200 characters")
    private String title;

    private String config;

    public CreateUiPageRequest() {
    }

    public CreateUiPageRequest(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public CreateUiPageRequest(String name, String path, String title, String config) {
        this.name = name;
        this.path = path;
        this.title = title;
        this.config = config;
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

    @Override
    public String toString() {
        return "CreateUiPageRequest{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}
