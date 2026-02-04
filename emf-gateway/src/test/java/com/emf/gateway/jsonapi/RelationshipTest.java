package com.emf.gateway.jsonapi;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipTest {

    @Test
    void shouldCreateRelationshipWithSingleResource() {
        ResourceIdentifier identifier = new ResourceIdentifier("users", "123");
        Relationship relationship = new Relationship(identifier);
        
        assertTrue(relationship.isSingleResource());
        assertFalse(relationship.isResourceCollection());
        assertEquals(identifier, relationship.getDataAsSingle());
        assertNull(relationship.getDataAsCollection());
    }

    @Test
    void shouldCreateRelationshipWithResourceCollection() {
        List<ResourceIdentifier> identifiers = Arrays.asList(
            new ResourceIdentifier("users", "123"),
            new ResourceIdentifier("users", "456")
        );
        Relationship relationship = new Relationship(identifiers);
        
        assertFalse(relationship.isSingleResource());
        assertTrue(relationship.isResourceCollection());
        assertNull(relationship.getDataAsSingle());
        assertEquals(identifiers, relationship.getDataAsCollection());
    }

    @Test
    void shouldSupportLinks() {
        ResourceIdentifier identifier = new ResourceIdentifier("users", "123");
        Map<String, String> links = new HashMap<>();
        links.put("self", "/api/posts/1/relationships/author");
        links.put("related", "/api/posts/1/author");
        
        Relationship relationship = new Relationship(identifier, links);
        
        assertEquals(links, relationship.getLinks());
    }

    @Test
    void shouldSupportDefaultConstructor() {
        Relationship relationship = new Relationship();
        
        assertNull(relationship.getData());
        assertNull(relationship.getLinks());
    }

    @Test
    void shouldSupportSetters() {
        Relationship relationship = new Relationship();
        ResourceIdentifier identifier = new ResourceIdentifier("users", "123");
        Map<String, String> links = new HashMap<>();
        links.put("self", "/api/posts/1/relationships/author");
        
        relationship.setData(identifier);
        relationship.setLinks(links);
        
        assertEquals(identifier, relationship.getData());
        assertEquals(links, relationship.getLinks());
    }

    @Test
    void shouldHandleNullData() {
        Relationship relationship = new Relationship(null);
        
        assertFalse(relationship.isSingleResource());
        assertFalse(relationship.isResourceCollection());
        assertNull(relationship.getDataAsSingle());
        assertNull(relationship.getDataAsCollection());
    }

    @Test
    void shouldProvideToString() {
        ResourceIdentifier identifier = new ResourceIdentifier("users", "123");
        Relationship relationship = new Relationship(identifier);
        String str = relationship.toString();
        
        assertTrue(str.contains("Relationship"));
        assertTrue(str.contains("data="));
    }
}
