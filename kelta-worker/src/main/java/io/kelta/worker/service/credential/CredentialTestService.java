package io.kelta.worker.service.credential;

import io.kelta.crypto.EncryptionService;
import io.kelta.runtime.credential.CredentialMaterial;
import io.kelta.runtime.credential.CredentialTestResult;
import io.kelta.runtime.credential.CredentialType;
import io.kelta.runtime.credential.CredentialTypeRegistry;
import io.kelta.worker.repository.CredentialRepository;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Tests a credential's connectivity. Dispatches on {@link CredentialType}:
 * <ul>
 *   <li>OAuth2 types — let the type itself perform a token exchange.</li>
 *   <li>SMTP — connect via Jakarta Mail; this lives here (not on the type)
 *       because Jakarta Mail is in {@code kelta-worker}, not {@code runtime-core}.</li>
 *   <li>All others — defer to the type's {@link CredentialType#test} hook.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "kelta.encryption.key")
public class CredentialTestService {

    private static final Logger log = LoggerFactory.getLogger(CredentialTestService.class);

    private final CredentialTypeRegistry typeRegistry;
    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public CredentialTestService(CredentialTypeRegistry typeRegistry,
                                  CredentialRepository credentialRepository,
                                  EncryptionService encryptionService,
                                  ObjectMapper objectMapper) {
        this.typeRegistry = typeRegistry;
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Tests a saved credential by ID, decrypting its stored material first.
     * Persists the test outcome in the credential row.
     */
    public CredentialTestResult test(String credentialId, String tenantId) {
        Optional<Map<String, Object>> row = credentialRepository.findById(credentialId, tenantId);
        if (row.isEmpty()) {
            return CredentialTestResult.failure("Credential not found");
        }
        Map<String, Object> record = row.get();

        String typeKey = (String) record.get("type");
        CredentialType type = typeRegistry.find(typeKey)
            .orElse(null);
        if (type == null) {
            return CredentialTestResult.failure("Unknown credential type: " + typeKey);
        }

        ObjectNode plaintext = decryptMaterial((String) record.get("data_enc"));
        ObjectNode metadata = parseMetadata(record.get("metadata"));
        CredentialTestResult result = doTest(type, plaintext, metadata);

        credentialRepository.updateTestStatus(
            credentialId, tenantId,
            result.ok() ? "OK" : "FAILED",
            result.ok() ? null : result.message());
        return result;
    }

    /**
     * Tests credential material before it has been saved. The caller passes a
     * type key and a flat plaintext payload (the same shape the credential
     * collection accepts on POST). Used by the "Test before save" UI affordance.
     */
    public CredentialTestResult testInline(String typeKey, Map<String, Object> plaintext) {
        CredentialType type = typeRegistry.find(typeKey).orElse(null);
        if (type == null) {
            return CredentialTestResult.failure("Unknown credential type: " + typeKey);
        }
        ObjectNode plaintextNode = objectMapper.valueToTree(plaintext);
        ObjectNode metadata = objectMapper.createObjectNode();
        for (String field : type.getMetadataFields()) {
            if (plaintextNode.has(field)) {
                metadata.set(field, plaintextNode.get(field));
            }
        }
        return doTest(type, plaintextNode, metadata);
    }

    private CredentialTestResult doTest(CredentialType type, ObjectNode plaintext, ObjectNode metadata) {
        try {
            if ("smtp".equals(type.getKey())) {
                return testSmtp(plaintext, metadata);
            }
            return type.test(new CredentialMaterial(type.getKey(), plaintext), metadata);
        } catch (Exception e) {
            log.warn("Credential test failed for type {}: {}", type.getKey(), e.getMessage());
            return CredentialTestResult.failure("Test failed: " + e.getMessage());
        }
    }

    private CredentialTestResult testSmtp(ObjectNode plaintext, ObjectNode metadata) {
        String host = textOrNull(metadata, "host");
        Integer port = intOrNull(metadata, "port");
        boolean useTls = boolOrDefault(metadata, "useTls", false);
        boolean useStartTls = boolOrDefault(metadata, "useStartTls", true);
        String username = textOrNull(plaintext, "username");
        String password = textOrNull(plaintext, "password");

        if (host == null || port == null || username == null || password == null) {
            return CredentialTestResult.failure(
                "host, port, username, and password are required");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        if (useTls) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (useStartTls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Session session = Session.getInstance(props);
        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(host, port, username, password);
            return CredentialTestResult.success(
                "Connected to " + host + ":" + port + " as " + username);
        } catch (Exception e) {
            return CredentialTestResult.failure(
                "SMTP connect failed: " + e.getMessage());
        }
    }

    private ObjectNode decryptMaterial(String dataEnc) {
        if (dataEnc == null || dataEnc.isBlank()) {
            return objectMapper.createObjectNode();
        }
        String json = encryptionService.decrypt(dataEnc);
        try {
            JsonNode node = objectMapper.readTree(json);
            return node instanceof ObjectNode obj ? obj : objectMapper.createObjectNode();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse decrypted credential material", e);
        }
    }

    private ObjectNode parseMetadata(Object metadata) {
        if (metadata == null) {
            return objectMapper.createObjectNode();
        }
        if (metadata instanceof Map<?, ?> map) {
            return objectMapper.valueToTree(new HashMap<>(map));
        }
        try {
            JsonNode node = objectMapper.readTree(metadata.toString());
            return node instanceof ObjectNode obj ? obj : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isTextual()) {
            String s = v.stringValue();
            return s.isBlank() ? null : s;
        }
        return v.toString();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asInt() : null;
    }

    private static boolean boolOrDefault(JsonNode node, String field, boolean fallback) {
        JsonNode v = node.get(field);
        return v != null && v.isBoolean() ? v.asBoolean() : fallback;
    }
}
