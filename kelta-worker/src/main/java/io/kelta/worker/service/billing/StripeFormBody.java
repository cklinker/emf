package io.kelta.worker.service.billing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds {@code application/x-www-form-urlencoded} bodies in the processor's
 * bracket notation — {@code metadata[userId]=u1},
 * {@code line_items[0][price]=price_1}, {@code automatic_tax[enabled]=true}.
 *
 * <p>Insertion-ordered so a request serializes deterministically, which is what
 * makes {@code MockRestServiceServer} assertions and log diffs readable. Every
 * key and value is percent-encoded; nothing is interpolated raw.
 */
final class StripeFormBody {

    private final Map<String, String> params = new LinkedHashMap<>();

    /** Adds {@code key=value}, skipping null values (the processor treats absent and null alike). */
    StripeFormBody add(String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
        return this;
    }

    StripeFormBody add(String key, boolean value) {
        params.put(key, Boolean.toString(value));
        return this;
    }

    /** Adds {@code prefix[k]=v} for each entry, skipping null keys and values. */
    StripeFormBody addMap(String prefix, Map<String, String> values) {
        if (values != null) {
            values.forEach((k, v) -> {
                if (k != null && v != null) {
                    params.put(prefix + "[" + k + "]", v);
                }
            });
        }
        return this;
    }

    boolean isEmpty() {
        return params.isEmpty();
    }

    String encode() {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    @Override
    public String toString() {
        return encode();
    }
}
