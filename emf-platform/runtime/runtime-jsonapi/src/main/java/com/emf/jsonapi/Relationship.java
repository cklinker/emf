package com.emf.jsonapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents a JSON:API relationship.
 * The data field can be either a single ResourceIdentifier or a List of ResourceIdentifiers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Relationship {
    private Object data;  // Can be ResourceIdentifier or List<ResourceIdentifier>
    private Map<String, String> links;

    public Relationship() {
    }

    public Relationship(Object data) {
        this.data = data;
    }

    public Relationship(Object data, Map<String, String> links) {
        this.data = data;
        this.links = links;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(Map<String, String> links) {
        this.links = links;
    }

    /**
     * Check if this relationship contains a single resource identifier.
     */
    @JsonIgnore
    public boolean isSingleResource() {
        return data instanceof ResourceIdentifier;
    }

    /**
     * Check if this relationship contains multiple resource identifiers.
     */
    @JsonIgnore
    public boolean isResourceCollection() {
        return data instanceof List;
    }

    /**
     * Get the data as a single ResourceIdentifier.
     * @return ResourceIdentifier or null if data is not a single resource
     */
    @JsonIgnore
    public ResourceIdentifier getDataAsSingle() {
        if (data instanceof ResourceIdentifier) {
            return (ResourceIdentifier) data;
        }
        return null;
    }

    /**
     * Get the data as a List of ResourceIdentifiers.
     * @return List of ResourceIdentifiers or null if data is not a collection
     */
    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<ResourceIdentifier> getDataAsCollection() {
        if (data instanceof List) {
            return (List<ResourceIdentifier>) data;
        }
        return null;
    }

    @Override
    public String toString() {
        return "Relationship{" +
                "data=" + data +
                ", links=" + links +
                '}';
    }
}
