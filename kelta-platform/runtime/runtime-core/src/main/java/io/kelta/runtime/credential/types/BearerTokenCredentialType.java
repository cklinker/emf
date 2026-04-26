package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * Static bearer token. Sent as {@code Authorization: Bearer <token>}.
 */
@Component
public class BearerTokenCredentialType extends AbstractCredentialType {

    public BearerTokenCredentialType(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override public String getKey()         { return "bearer_token"; }
    @Override public String getDisplayName() { return "Bearer Token"; }
    @Override public String getDescription() {
        return "A static token sent as Authorization: Bearer.";
    }

    @Override public Set<String> getSecretFields()   { return Set.of("token"); }
    @Override public Set<String> getMetadataFields() { return Set.of(); }

    @Override
    public List<String> validate(ObjectNode plaintext) {
        return validateRequired(plaintext, "token");
    }

    @Override
    public CredentialTestResult test(CredentialMaterial material, ObjectNode metadata) {
        return CredentialTestResult.success(
            "Stored. Bearer tokens cannot be tested without a target endpoint.");
    }
}
