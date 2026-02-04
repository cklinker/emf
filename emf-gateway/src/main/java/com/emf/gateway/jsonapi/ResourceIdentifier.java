package com.emf.gateway.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a JSON:API resource identifier with type and id.
 * Used in relationships to reference other resources.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceIdentifier {
    private String type;
    private String id;

    public ResourceIdentifier() {
    }

    public ResourceIdentifier(String type, String id) {
        this.type = type;
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceIdentifier that = (ResourceIdentifier) o;
        return type != null && type.equals(that.type) && id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ResourceIdentifier{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
