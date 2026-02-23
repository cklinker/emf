package com.emf.jsonapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceIdentifierTest {

    @Test
    void shouldCreateResourceIdentifierWithTypeAndId() {
        ResourceIdentifier identifier = new ResourceIdentifier("users", "123");

        assertEquals("users", identifier.getType());
        assertEquals("123", identifier.getId());
    }

    @Test
    void shouldSupportDefaultConstructor() {
        ResourceIdentifier identifier = new ResourceIdentifier();

        assertNull(identifier.getType());
        assertNull(identifier.getId());
    }

    @Test
    void shouldSupportSetters() {
        ResourceIdentifier identifier = new ResourceIdentifier();
        identifier.setType("posts");
        identifier.setId("456");

        assertEquals("posts", identifier.getType());
        assertEquals("456", identifier.getId());
    }

    @Test
    void shouldImplementEqualsCorrectly() {
        ResourceIdentifier id1 = new ResourceIdentifier("users", "123");
        ResourceIdentifier id2 = new ResourceIdentifier("users", "123");
        ResourceIdentifier id3 = new ResourceIdentifier("users", "456");
        ResourceIdentifier id4 = new ResourceIdentifier("posts", "123");

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id1, id4);
        assertNotEquals(id1, null);
        assertNotEquals(id1, "not a ResourceIdentifier");
    }

    @Test
    void shouldImplementHashCodeCorrectly() {
        ResourceIdentifier id1 = new ResourceIdentifier("users", "123");
        ResourceIdentifier id2 = new ResourceIdentifier("users", "123");

        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void shouldHandleNullInEquals() {
        ResourceIdentifier id1 = new ResourceIdentifier(null, null);
        ResourceIdentifier id2 = new ResourceIdentifier(null, null);

        // Should not throw exception
        assertNotEquals(id1, id2);
    }

    @Test
    void shouldProvideToString() {
        ResourceIdentifier identifier = new ResourceIdentifier("users", "123");
        String str = identifier.toString();

        assertTrue(str.contains("users"));
        assertTrue(str.contains("123"));
    }
}
