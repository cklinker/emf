package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DTO representing authorization hints for a resource (collection).
 * Contains information about which policies apply to which operations.
 * 
 * <p>Requirements satisfied:
 * <ul>
 *   <li>8.4: Include authorization hints for each collection</li>
 * </ul>
 */
public class AuthorizationHintsDto {

    /**
     * Map of operation name to list of policy names that apply to that operation.
     * Example: {"CREATE": ["admin-only"], "READ": ["authenticated"], "UPDATE": ["admin-only"], "DELETE": ["admin-only"]}
     */
    private Map<String, List<String>> operationPolicies;

    public AuthorizationHintsDto() {
    }

    public AuthorizationHintsDto(Map<String, List<String>> operationPolicies) {
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
        AuthorizationHintsDto that = (AuthorizationHintsDto) o;
        return Objects.equals(operationPolicies, that.operationPolicies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationPolicies);
    }

    @Override
    public String toString() {
        return "AuthorizationHintsDto{" +
                "operationPolicies=" + operationPolicies +
                '}';
    }
}
