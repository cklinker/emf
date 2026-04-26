package io.kelta.worker.listener;

import io.kelta.crypto.EncryptionService;
import io.kelta.runtime.credential.CredentialTypeRegistry;
import io.kelta.runtime.credential.types.ApiKeyCredentialType;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CredentialEncryptionHook")
class CredentialEncryptionHookTest {

    private static final String TEST_KEY =
        Base64.getEncoder().encodeToString(new byte[32]);

    private CredentialEncryptionHook hook;
    private EncryptionService encryptionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(TEST_KEY);
        objectMapper = new ObjectMapper();
        CredentialTypeRegistry registry = new CredentialTypeRegistry(
            List.of(new ApiKeyCredentialType(objectMapper)));
        hook = new CredentialEncryptionHook(encryptionService, registry, objectMapper);
    }

    @Test
    @DisplayName("Targets the credentials collection")
    void targetsCredentials() {
        assertEquals("credentials", hook.getCollectionName());
    }

    @Test
    @DisplayName("Runs early so plaintext is encrypted before audit logging sees it")
    void runsEarly() {
        assertEquals(-100, hook.getOrder());
    }

    @Test
    @DisplayName("Encrypts secret fields and strips plaintext on create")
    void encryptsSecretsAndStripsPlaintext() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "type", "api_key",
            "name", "test-cred",
            "headerName", "X-API-Key",
            "value", "super-secret-value"));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertTrue(result.isSuccess(), "should succeed");
        assertTrue(result.hasFieldUpdates(), "should produce field updates");

        // Plaintext value field should be stripped from the record so it never
        // reaches the storage adapter.
        assertFalse(record.containsKey("value"),
            "plaintext value must be stripped from the record");
        assertFalse(record.containsKey("headerName"),
            "plaintext headerName must be stripped from the record (moved to metadata)");

        // dataEnc should be in the field updates and look like an encrypted blob.
        Object dataEnc = result.getFieldUpdates().get("dataEnc");
        assertNotNull(dataEnc, "dataEnc must be set");
        assertTrue(dataEnc.toString().startsWith("enc:v1:"),
            "dataEnc must use the encryption envelope prefix");

        // Roundtrip: decrypting dataEnc should recover the original secret.
        String decrypted = encryptionService.decrypt(dataEnc.toString());
        assertTrue(decrypted.contains("super-secret-value"),
            "decrypted blob should contain the original secret");

        // Metadata should contain the non-secret companion fields.
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata =
            (Map<String, Object>) result.getFieldUpdates().get("metadata");
        assertNotNull(metadata, "metadata should be populated");
        assertEquals("X-API-Key", metadata.get("headerName"));
    }

    @Test
    @DisplayName("Skips re-encryption when only metadata is being updated")
    void skipsReEncryptionForMetadataOnlyUpdate() {
        Map<String, Object> previous = Map.of(
            "type", "api_key",
            "dataEnc", encryptionService.encrypt("{\"value\":\"old\"}"),
            "metadata", Map.of("headerName", "X-API-Key"));
        Map<String, Object> update = new HashMap<>(Map.of(
            "type", "api_key",
            "description", "Updated description"));

        BeforeSaveResult result = hook.beforeUpdate("c-1", update, previous, "tenant-1");

        assertTrue(result.isSuccess());
        // dataEnc should not be in the field updates because no secret fields were touched
        assertFalse(result.getFieldUpdates().containsKey("dataEnc"),
            "secret-only blob should be untouched on metadata-only updates");
    }

    @Test
    @DisplayName("Returns a validation error for unknown credential types")
    void rejectsUnknownTypes() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "type", "completely_invented",
            "value", "x"));

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
        assertEquals("type", result.getErrors().get(0).field());
    }

    @Test
    @DisplayName("Returns a validation error for missing required fields")
    void rejectsMissingRequiredFields() {
        Map<String, Object> record = new HashMap<>(Map.of(
            "type", "api_key",
            "name", "test-cred"));   // no headerName, no value

        BeforeSaveResult result = hook.beforeCreate(record, "tenant-1");

        assertFalse(result.isSuccess());
        assertTrue(result.hasErrors());
    }
}
