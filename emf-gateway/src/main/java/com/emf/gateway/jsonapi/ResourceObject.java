package com.emf.gateway.jsonapi;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a JSON:API resource object with type, id, attributes, and relationships.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ResourceObject {
    private String type;
    private String id;
    private Map<String, Object> attributes;
    private Map<String, Relationship> relationships;

    public ResourceObject() {
    }

    public ResourceObject(String type, String id) {
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

    public Map<String, Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Relationship> getRelationships() {
        if (relationships == null) {
            relationships = new HashMap<>();
        }
        return relationships;
    }

    public void setRelationships(Map<String, Relationship> relationships) {
        this.relationships = relationships;
    }

    /**
     * Add an attribute to this resource.
     */
    public void addAttribute(String name, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(name, value);
    }

    /**
     * Remove an attribute from this resource.
     */
    public void removeAttribute(String name) {
        if (this.attributes != null) {
            this.attributes.remove(name);
        }
    }

    /**
     * Add a relationship to this resource.
     */
    public void addRelationship(String name, Relationship relationship) {
        if (this.relationships == null) {
            this.relationships = new HashMap<>();
        }
        this.relationships.put(name, relationship);
    }

    /**
     * Get a resource identifier for this resource.
     */
    public ResourceIdentifier toResourceIdentifier() {
        return new ResourceIdentifier(type, id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceObject that = (ResourceObject) o;
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
        return "ResourceObject{" +
                "type='" + type + '\'' +
                ", id='" + id + '\'' +
                ", attributes=" + attributes +
                ", relationships=" + relationships +
                '}';
    }
}
