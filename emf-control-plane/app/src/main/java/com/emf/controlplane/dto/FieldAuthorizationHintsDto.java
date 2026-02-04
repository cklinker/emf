package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DTO representing authorization hints for a field within a resource.
 * Contains information about which policies apply to which field operations.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.4: Include authorization hints for each field</li>
 * </ul>
 */
public class FieldAuthorizationHintsDto {

    /**
     * Map of operation name to list of policy names that apply to that field operation.
     * Example: {"READ": ["authenticated"], "WRITE": ["admin-only"]}
     */
    private Map<String, List<String>> operationPolicies;

    public FieldAuthorizationHintsDto() {
    }

    public FieldAuthorizationHintsDto(Map<String, List<String>> operationPolicies) {
        this.operationPolicies = operationPolicies;
    }

    public Map<String, List<String>> getOperationPolicies() {
        return operationPolicies;
    }

    public void setOperationPolicies(Map<String, List<String>> operationPolicies) {
        this.operationPolicies = operationPolicies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAuthorizationHintsDto that = (FieldAuthorizationHintsDto) o;
        return Objects.equals(operationPolicies, that.operationPolicies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationPolicies);
    }

    @Override
    public String toString() {
        return "FieldAuthorizationHintsDto{" +
                "operationPolicies=" + operationPolicies +
                '}';
    }
}
