package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * HTTP Basic auth — username and password concatenated and Base64-encoded
 * into the {@code Authorization} header.
 */
@Component
public class BasicAuthCredentialType extends AbstractCredentialType {

    public BasicAuthCredentialType(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override public String getKey()         { return "basic_auth"; }
    @Override public String getDisplayName() { return "Basic Auth"; }
    @Override public String getDescription() {
        return "HTTP Basic — username and password sent as Authorization: Basic.";
    }

    @Override public Set<String> getSecretFields()   { return Set.of("username", "password"); }
    @Override public Set<String> getMetadataFields() { return Set.of(); }

    @Override
    public List<String> validate(ObjectNode plaintext) {
        return validateRequired(plaintext, "username", "password");
    }

    @Override
    public CredentialTestResult test(CredentialMaterial material, ObjectNode metadata) {
        return CredentialTestResult.success(
            "Stored. Basic auth cannot be tested without a target endpoint.");
    }
}
