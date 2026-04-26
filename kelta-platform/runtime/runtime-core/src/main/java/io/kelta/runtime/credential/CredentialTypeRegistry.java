package io.kelta.runtime.credential;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of all available {@link CredentialType} implementations.
 *
 * <p>Spring auto-wires the {@code List<CredentialType>} discovered via component
 * scanning and constructs one of these per application context.
 */
public class CredentialTypeRegistry {

    private final Map<String, CredentialType> typesByKey = new LinkedHashMap<>();

    public CredentialTypeRegistry(List<CredentialType> types) {
        for (CredentialType t : types) {
            typesByKey.put(t.getKey(), t);
        }
    }

    public Optional<CredentialType> find(String key) {
        return Optional.ofNullable(typesByKey.get(key));
    }

    public CredentialType require(String key) {
        CredentialType t = typesByKey.get(key);
        if (t == null) {
            throw new IllegalArgumentException("Unknown credential type: " + key);
        }
        return t;
    }

    public Collection<CredentialType> all() {
        return typesByKey.values();
    }

    public boolean contains(String key) {
        return typesByKey.containsKey(key);
    }
}
