package com.emf.gateway.jsonapi;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceObjectTest {

    @Test
    void shouldCreateResourceObjectWithTypeAndId() {
        ResourceObject resource = new ResourceObject("users", "123");
        
        assertEquals("users", resource.getType());
        assertEquals("123", resource.getId());
        assertNotNull(resource.getAttributes());
        assertNotNull(resource.getRelationships());
    }

    @Test
    void shouldSupportDefaultConstructor() {
        ResourceObject resource = new ResourceObject();
        
        assertNull(resource.getType());
        assertNull(resource.getId());
        assertNotNull(resource.getAttributes());
        assertNotNull(resource.getRelationships());
    }

    @Test
    void shouldSupportSetters() {
        ResourceObject resource = new ResourceObject();
        resource.setType("posts");
        resource.setId("456");
        
        assertEquals("posts", resource.getType());
        assertEquals("456", resource.getId());
    }

    @Test
    void shouldAddAndRemoveAttributes() {
        ResourceObject resource = new ResourceObject("users", "123");
        
        resource.addAttribute("name", "John Doe");
        resource.addAttribute("email", "john@example.com");
        
        assertEquals("John Doe", resource.getAttributes().get("name"));
        assertEquals("john@example.com", resource.getAttributes().get("email"));
        assertEquals(2, resource.getAttributes().size());
        
        resource.removeAttribute("email");
        
        assertEquals(1, resource.getAttributes().size());
        assertNull(resource.getAttributes().get("email"));
    }

    @Test
    void shouldSetAttributesMap() {
        ResourceObject resource = new ResourceObject("users", "123");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", "Jane Doe");
        attributes.put("age", 30);
        
        resource.setAttributes(attributes);
        
        assertEquals(attributes, resource.getAttributes());
    }

    @Test
    void shouldAddRelationships() {
        ResourceObject resource = new ResourceObject("posts", "1");
        ResourceIdentifier authorId = new ResourceIdentifier("users", "123");
        Relationship authorRelationship = new Relationship(authorId);
        
        resource.addRelationship("author", authorRelationship);
        
        assertEquals(1, resource.getRelationships().size());
        assertEquals(authorRelationship, resource.getRelationships().get("author"));
    }

    @Test
    void shouldSetRelationshipsMap() {
        ResourceObject resource = new ResourceObject("posts", "1");
        Map<String, Relationship> relationships = new HashMap<>();
        relationships.put("author", new Relationship(new ResourceIdentifier("users", "123")));
        
        resource.setRelationships(relationships);
        
        assertEquals(relationships, resource.getRelationships());
    }

    @Test
    void shouldConvertToResourceIdentifier() {
        ResourceObject resource = new ResourceObject("users", "123");
        ResourceIdentifier identifier = resource.toResourceIdentifier();
        
        assertEquals("users", identifier.getType());
        assertEquals("123", identifier.getId());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        ResourceObject resource1 = new ResourceObject("users", "123");
        ResourceObject resource2 = new ResourceObject("users", "123");
        ResourceObject resource3 = new ResourceObject("users", "456");
        ResourceObject resource4 = new ResourceObject("posts", "123");
        
        assertEquals(resource1, resource2);
        assertNotEquals(resource1, resource3);
        assertNotEquals(resource1, resource4);
        assertNotEquals(resource1, null);
        assertNotEquals(resource1, "not a ResourceObject");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
        ResourceObject resource1 = new ResourceObject("users", "123");
        ResourceObject resource2 = new ResourceObject("users", "123");
        
        assertEquals(resource1.hashCode(), resource2.hashCode());
    }

    @Test
    void shouldHandleNullAttributesInAddAttribute() {
        ResourceObject resource = new ResourceObject("users", "123");
        resource.setAttributes(null);
        
        resource.addAttribute("name", "John");
        
        assertNotNull(resource.getAttributes());
        assertEquals("John", resource.getAttributes().get("name"));
    }

    @Test
    void shouldHandleNullAttributesInRemoveAttribute() {
        ResourceObject resource = new ResourceObject("users", "123");
        resource.setAttributes(null);
        
        // Should not throw exception
        resource.removeAttribute("name");
    }

    @Test
    void shouldHandleNullRelationshipsInAddRelationship() {
        ResourceObject resource = new ResourceObject("users", "123");
        resource.setRelationships(null);
        
        Relationship relationship = new Relationship(new ResourceIdentifier("posts", "1"));
        resource.addRelationship("posts", relationship);
        
        assertNotNull(resource.getRelationships());
        assertEquals(relationship, resource.getRelationships().get("posts"));
    }

    @Test
    void shouldProvideToString() {
        ResourceObject resource = new ResourceObject("users", "123");
        resource.addAttribute("name", "John");
        String str = resource.toString();
        
        assertTrue(str.contains("users"));
        assertTrue(str.contains("123"));
    }
}
