package io.kelta.worker.service.credential;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaces the {@code dataEnc} field on credential records before they are
 * returned to API clients with a fixed sentinel ({@code "***"}).
 *
 * <p>The encrypted blob is opaque ciphertext, but exposing it would still
 * leak metadata an attacker could correlate against, so we strip it
 * unconditionally on read.
 */
@Component
public class CredentialResponseMasker {

    public static final String MASK = "***";
    private static final String DATA_ENC_FIELD = "dataEnc";

    /**
     * Returns a masked copy of {@code record}. Leaves the input untouched so
     * callers can mask once and reuse the source elsewhere.
     */
    public Map<String, Object> mask(Map<String, Object> record) {
        if (record == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(record);
        if (copy.containsKey(DATA_ENC_FIELD)) {
            copy.put(DATA_ENC_FIELD, MASK);
        }
        return copy;
    }
}
