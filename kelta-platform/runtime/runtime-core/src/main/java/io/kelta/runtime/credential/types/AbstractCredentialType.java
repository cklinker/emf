package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for {@link CredentialType} implementations. Loads the input
 * schema from {@code /credential-schemas/<key>.input.json} at construction.
 */
public abstract class AbstractCredentialType implements CredentialType {

    private final JsonNode inputSchema;

    protected AbstractCredentialType(ObjectMapper objectMapper) {
        this.inputSchema = loadSchema(objectMapper, getKey());
    }

    @Override
    public final JsonNode getInputSchema() {
        return inputSchema;
    }

    private static JsonNode loadSchema(ObjectMapper objectMapper, String typeKey) {
        String resource = "/credential-schemas/" + typeKey + ".input.json";
        try (InputStream in = AbstractCredentialType.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing credential schema: " + resource);
            }
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load credential schema " + resource, e);
        }
    }

    /** Validates that all required fields named in the schema are present and non-blank. */
    protected List<String> validateRequired(ObjectNode plaintext, String... fieldNames) {
        List<String> errors = new ArrayList<>();
        for (String name : fieldNames) {
            JsonNode v = plaintext.get(name);
            if (v == null || v.isNull()
                || (v.isTextual() && v.stringValue().isBlank())) {
                errors.add(name + " is required");
            }
        }
        return errors;
    }

    /** Returns the textual value for {@code field}, or {@code null} when missing/blank. */
    protected String string(ObjectNode plaintext, String field) {
        JsonNode v = plaintext.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.isTextual() ? v.stringValue() : v.toString();
        return s.isBlank() ? null : s;
    }
}
