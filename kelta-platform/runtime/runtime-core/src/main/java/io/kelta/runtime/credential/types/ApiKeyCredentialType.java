package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * Static API-key credential. The key is sent verbatim as a header or query
 * parameter on each request.
 *
 * <p>There is no provider to authenticate against without a target URL, so
 * {@link #test} returns a "format valid" result without making a network call.
 */
@Component
public class ApiKeyCredentialType extends AbstractCredentialType {

    public ApiKeyCredentialType(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override public String getKey()         { return "api_key"; }
    @Override public String getDisplayName() { return "API Key"; }
    @Override public String getDescription() {
        return "A static key sent as an HTTP header or query parameter.";
    }

    @Override public Set<String> getSecretFields()   { return Set.of("value"); }
    @Override public Set<String> getMetadataFields() { return Set.of("location", "headerName", "prefix"); }

    @Override
    public List<String> validate(ObjectNode plaintext) {
        return validateRequired(plaintext, "headerName", "value");
    }

    @Override
    public CredentialTestResult test(CredentialMaterial material, ObjectNode metadata) {
        return CredentialTestResult.success(
            "Stored. API keys cannot be tested without a target endpoint.");
    }
}
