package io.kelta.worker.service.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CredentialResponseMasker")
class CredentialResponseMaskerTest {

    private final CredentialResponseMasker masker = new CredentialResponseMasker();

    @Test
    @DisplayName("Replaces dataEnc with the mask sentinel")
    void replacesDataEnc() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "c-1");
        record.put("name", "github-token");
        record.put("dataEnc", "enc:v1:abcdef:0123456789");

        Map<String, Object> masked = masker.mask(record);

        assertEquals(CredentialResponseMasker.MASK, masked.get("dataEnc"));
        assertEquals("c-1", masked.get("id"));
        assertEquals("github-token", masked.get("name"));
    }

    @Test
    @DisplayName("Leaves the input record untouched")
    void leavesInputUntouched() {
        Map<String, Object> record = new HashMap<>();
        record.put("dataEnc", "enc:v1:secret");

        masker.mask(record);

        // Source object must be unchanged so callers can re-use it
        assertEquals("enc:v1:secret", record.get("dataEnc"));
    }

    @Test
    @DisplayName("Tolerates records without dataEnc")
    void tolerantOfMissingField() {
        Map<String, Object> record = new HashMap<>();
        record.put("id", "c-1");

        Map<String, Object> masked = masker.mask(record);

        assertFalse(masked.containsKey("dataEnc"));
        assertEquals("c-1", masked.get("id"));
    }

    @Test
    @DisplayName("Returns null when given null")
    void returnsNullForNullInput() {
        assertNull(masker.mask(null));
    }
}
