package com.emf.runtime.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManifestParserTest {

    private ModuleManifestParser parser;

    @BeforeEach
    void setUp() {
        parser = new ModuleManifestParser(new ObjectMapper());
    }

    @Test
    void shouldParseMinimalManifest() {
        String json = """
            {
              "id": "test-module",
              "name": "Test Module",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule"
            }
            """;

        ModuleManifest manifest = parser.parse(json);

        assertEquals("test-module", manifest.id());
        assertEquals("Test Module", manifest.name());
        assertEquals("1.0.0", manifest.version());
        assertEquals("com.test.TestModule", manifest.moduleClass());
        assertNull(manifest.description());
        assertNull(manifest.author());
        assertNull(manifest.minPlatformVersion());
        assertTrue(manifest.permissions().isEmpty());
        assertTrue(manifest.actionHandlers().isEmpty());
    }

    @Test
    void shouldParseFullManifest() {
        String json = """
            {
              "id": "stripe-integration",
              "name": "Stripe Integration",
              "version": "1.2.0",
              "description": "Stripe payment processing",
              "author": "EMF Marketplace",
              "moduleClass": "com.stripe.emf.StripeModule",
              "minPlatformVersion": "1.0.0",
              "permissions": ["HTTP_OUTBOUND", "READ_RECORDS"],
              "actionHandlers": [
                {
                  "key": "stripe:charge",
                  "name": "Create Charge",
                  "category": "Payment",
                  "description": "Create a Stripe payment charge",
                  "icon": "CreditCard",
                  "configSchema": "{\\"type\\": \\"object\\"}",
                  "inputSchema": "{\\"type\\": \\"object\\"}",
                  "outputSchema": "{\\"type\\": \\"object\\"}"
                }
              ]
            }
            """;

        ModuleManifest manifest = parser.parse(json);

        assertEquals("stripe-integration", manifest.id());
        assertEquals("Stripe Integration", manifest.name());
        assertEquals("1.2.0", manifest.version());
        assertEquals("Stripe payment processing", manifest.description());
        assertEquals("EMF Marketplace", manifest.author());
        assertEquals("com.stripe.emf.StripeModule", manifest.moduleClass());
        assertEquals("1.0.0", manifest.minPlatformVersion());
        assertEquals(2, manifest.permissions().size());
        assertTrue(manifest.permissions().contains("HTTP_OUTBOUND"));

        assertEquals(1, manifest.actionHandlers().size());
        ModuleManifest.ActionHandlerManifest handler = manifest.actionHandlers().get(0);
        assertEquals("stripe:charge", handler.key());
        assertEquals("Create Charge", handler.name());
        assertEquals("Payment", handler.category());
        assertEquals("Create a Stripe payment charge", handler.description());
        assertEquals("CreditCard", handler.icon());
    }

    @Test
    void shouldFailOnMissingId() {
        String json = """
            {
              "name": "Test Module",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule"
            }
            """;

        assertThrows(ModuleManifestParser.ModuleManifestException.class,
            () -> parser.parse(json));
    }

    @Test
    void shouldFailOnMissingName() {
        String json = """
            {
              "id": "test",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule"
            }
            """;

        assertThrows(ModuleManifestParser.ModuleManifestException.class,
            () -> parser.parse(json));
    }

    @Test
    void shouldFailOnMissingVersion() {
        String json = """
            {
              "id": "test",
              "name": "Test",
              "moduleClass": "com.test.TestModule"
            }
            """;

        assertThrows(ModuleManifestParser.ModuleManifestException.class,
            () -> parser.parse(json));
    }

    @Test
    void shouldFailOnMissingModuleClass() {
        String json = """
            {
              "id": "test",
              "name": "Test",
              "version": "1.0.0"
            }
            """;

        assertThrows(ModuleManifestParser.ModuleManifestException.class,
            () -> parser.parse(json));
    }

    @Test
    void shouldFailOnInvalidJson() {
        assertThrows(ModuleManifestParser.ModuleManifestException.class,
            () -> parser.parse("not valid json"));
    }

    @Test
    void shouldFailOnNullInput() {
        assertThrows(NullPointerException.class,
            () -> parser.parse(null));
    }

    @Test
    void shouldParseActionHandlerWithMinimalFields() {
        String json = """
            {
              "id": "test",
              "name": "Test",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule",
              "actionHandlers": [
                { "key": "test:action", "name": "Test Action" }
              ]
            }
            """;

        ModuleManifest manifest = parser.parse(json);
        assertEquals(1, manifest.actionHandlers().size());

        ModuleManifest.ActionHandlerManifest handler = manifest.actionHandlers().get(0);
        assertEquals("test:action", handler.key());
        assertEquals("Test Action", handler.name());
        assertNull(handler.category());
        assertNull(handler.description());
        assertNull(handler.icon());
        assertNull(handler.configSchema());
    }

    @Test
    void shouldFailOnActionHandlerMissingKey() {
        String json = """
            {
              "id": "test",
              "name": "Test",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule",
              "actionHandlers": [
                { "name": "Test Action" }
              ]
            }
            """;

        assertThrows(ModuleManifestParser.ModuleManifestException.class,
            () -> parser.parse(json));
    }

    @Test
    void shouldIgnoreUnknownFields() {
        String json = """
            {
              "id": "test",
              "name": "Test",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule",
              "unknownField": "ignored"
            }
            """;

        ModuleManifest manifest = parser.parse(json);
        assertEquals("test", manifest.id());
    }

    @Test
    void shouldParseMultipleActionHandlers() {
        String json = """
            {
              "id": "test",
              "name": "Test",
              "version": "1.0.0",
              "moduleClass": "com.test.TestModule",
              "actionHandlers": [
                { "key": "action1", "name": "Action 1" },
                { "key": "action2", "name": "Action 2" },
                { "key": "action3", "name": "Action 3" }
              ]
            }
            """;

        ModuleManifest manifest = parser.parse(json);
        assertEquals(3, manifest.actionHandlers().size());
    }
}
