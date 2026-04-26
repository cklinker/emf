package io.kelta.runtime.credential;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads provider credential templates from {@code /credential-templates/*.json}
 * on the classpath. Each file is a single template document.
 */
public class CredentialTemplateRegistry {

    private static final String[] BUNDLED_TEMPLATES = new String[] {
        "salesforce", "slack", "github", "stripe", "google"
    };

    private final Map<String, CredentialTemplate> byKey = new LinkedHashMap<>();

    public CredentialTemplateRegistry(ObjectMapper objectMapper) {
        for (String key : BUNDLED_TEMPLATES) {
            CredentialTemplate template = loadTemplate(objectMapper, key);
            if (template != null) {
                byKey.put(template.key(), template);
            }
        }
    }

    public Collection<CredentialTemplate> all() {
        return byKey.values();
    }

    public Optional<CredentialTemplate> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    /** Returns templates whose target {@link CredentialType} key matches. */
    public List<CredentialTemplate> byType(String typeKey) {
        List<CredentialTemplate> out = new ArrayList<>();
        for (CredentialTemplate t : byKey.values()) {
            if (typeKey.equals(t.type())) {
                out.add(t);
            }
        }
        return out;
    }

    private static CredentialTemplate loadTemplate(ObjectMapper objectMapper, String key) {
        String resource = "/credential-templates/" + key + ".json";
        try (InputStream in = CredentialTemplateRegistry.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(in);
            return new CredentialTemplate(
                root.get("key").stringValue(),
                root.get("name").stringValue(),
                root.get("type").stringValue(),
                root.has("iconUrl") ? root.get("iconUrl").stringValue() : null,
                root.get("defaults"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template " + resource, e);
        }
    }
}
