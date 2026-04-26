package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * Free-form credential — arbitrary JSON object encrypted whole. Useful for
 * provider integrations that don't fit the standard auth schemes.
 */
@Component
public class CustomCredentialType extends AbstractCredentialType {

    public CustomCredentialType(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override public String getKey()         { return "custom"; }
    @Override public String getDisplayName() { return "Custom"; }
    @Override public String getDescription() {
        return "Free-form key/value JSON. Entire payload is encrypted.";
    }

    @Override public Set<String> getSecretFields()   { return Set.of("data"); }
    @Override public Set<String> getMetadataFields() { return Set.of(); }

    @Override
    public List<String> validate(ObjectNode plaintext) {
        JsonNode data = plaintext.get("data");
        if (data == null || data.isNull() || !data.isObject()) {
            return List.of("data must be a JSON object");
        }
        return List.of();
    }

    @Override
    public CredentialTestResult test(CredentialMaterial material, ObjectNode metadata) {
        return CredentialTestResult.success(
            "Stored. Custom credentials are not connectivity-tested.");
    }
}
