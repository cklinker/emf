package com.emf.runtime.validation;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for validation rules, keyed by collection name.
 *
 * <p>The worker populates this registry when initializing or refreshing
 * collections. The {@link CustomValidationRuleEngine} reads from it
 * during record create/update operations.
 */
public class ValidationRuleRegistry {

    private final ConcurrentHashMap<String, List<ValidationRuleDefinition>> rulesByCollection =
            new ConcurrentHashMap<>();

    /**
     * Registers (or replaces) the validation rules for a collection.
     *
     * @param collectionName the collection name
     * @param rules          the list of validation rule definitions
     */
    public void register(String collectionName, List<ValidationRuleDefinition> rules) {
        rulesByCollection.put(collectionName, List.copyOf(rules));
    }

    /**
     * Returns the active validation rules for a collection.
     *
     * @param collectionName the collection name
     * @return unmodifiable list of active rules, or empty list if none registered
     */
    public List<ValidationRuleDefinition> getActiveRules(String collectionName) {
        List<ValidationRuleDefinition> rules = rulesByCollection.getOrDefault(
                collectionName, Collections.emptyList());
        return rules.stream()
                .filter(ValidationRuleDefinition::active)
                .toList();
    }

    /**
     * Removes validation rules for a collection.
     *
     * @param collectionName the collection name
     */
    public void unregister(String collectionName) {
        rulesByCollection.remove(collectionName);
    }

    /**
     * Returns the total number of collections with registered rules.
     */
    public int size() {
        return rulesByCollection.size();
    }
}
