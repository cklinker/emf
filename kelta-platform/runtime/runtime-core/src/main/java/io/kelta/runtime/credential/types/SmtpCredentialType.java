package io.kelta.runtime.credential.types;

import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SMTP credential — host/port/username/password used by the email subsystem.
 *
 * <p>The {@link #test} method here only validates the input format; the worker's
 * {@code CredentialTestService} layers a real SMTP connection probe on top
 * because Jakarta Mail lives in the worker module, not in runtime-core.
 */
@Component
public class SmtpCredentialType extends AbstractCredentialType {

    public SmtpCredentialType(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override public String getKey()         { return "smtp"; }
    @Override public String getDisplayName() { return "SMTP"; }
    @Override public String getDescription() {
        return "Outbound mail server credentials (host, port, username, password).";
    }

    @Override public Set<String> getSecretFields()   { return Set.of("username", "password"); }
    @Override public Set<String> getMetadataFields() {
        return Set.of("host", "port", "useTls", "useStartTls", "fromAddress");
    }

    @Override
    public List<String> validate(ObjectNode plaintext) {
        List<String> errors = new ArrayList<>(
            validateRequired(plaintext, "host", "port", "username", "password"));
        JsonNode port = plaintext.get("port");
        if (port != null && port.isNumber()) {
            int p = port.asInt();
            if (p < 1 || p > 65535) {
                errors.add("port must be between 1 and 65535");
            }
        } else if (port != null && !port.isNull()) {
            errors.add("port must be a number");
        }
        return errors;
    }

    @Override
    public CredentialTestResult test(CredentialMaterial material, ObjectNode metadata) {
        return CredentialTestResult.success("Saved. Connectivity tested by the worker.");
    }
}
