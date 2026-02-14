package com.emf.controlplane.event;

import com.emf.controlplane.entity.Collection;
import com.emf.controlplane.entity.Field;
import com.emf.controlplane.entity.FieldPolicy;
import com.emf.controlplane.entity.RoutePolicy;
import com.emf.runtime.event.AuthzChangedPayload;
import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.CollectionChangedPayload;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter class to convert control-plane entities to shared event payloads.
 * 
 * This class bridges the gap between control-plane entities and the shared
 * runtime-events payloads, since the shared payloads cannot depend on
 * control-plane entity classes.
 */
public class PayloadAdapter {

    /**
     * Creates a CollectionChangedPayload from a Collection entity.
     */
    public static CollectionChangedPayload toCollectionPayload(Collection collection, ChangeType changeType) {
        CollectionChangedPayload payload = new CollectionChangedPayload();
        payload.setId(collection.getId());
        payload.setName(collection.getName());
        payload.setDisplayName(collection.getDisplayName());
        payload.setDescription(collection.getDescription());
        payload.setStorageMode(collection.getStorageMode());
        payload.setActive(collection.isActive());
        payload.setCurrentVersion(collection.getCurrentVersion());
        payload.setCreatedAt(collection.getCreatedAt());
        payload.setUpdatedAt(collection.getUpdatedAt());
        payload.setChangeType(changeType);

        // Include active fields in the payload
        if (collection.getFields() != null) {
            payload.setFields(collection.getFields().stream()
                    .filter(Field::isActive)
                    .map(PayloadAdapter::toFieldPayload)
                    .collect(Collectors.toList()));
        }

        return payload;
    }

    /**
     * Creates a FieldPayload from a Field entity.
     */
    private static CollectionChangedPayload.FieldPayload toFieldPayload(Field field) {
        CollectionChangedPayload.FieldPayload payload = new CollectionChangedPayload.FieldPayload();
        payload.setId(field.getId());
        payload.setName(field.getName());
        payload.setType(field.getType());
        payload.setRequired(field.isRequired());
        payload.setUnique(field.isUnique());
        payload.setDescription(field.getDescription());
        payload.setConstraints(field.getConstraints());
        payload.setFieldTypeConfig(field.getFieldTypeConfig());
        payload.setReferenceTarget(field.getReferenceTarget());
        payload.setRelationshipType(field.getRelationshipType());
        payload.setRelationshipName(field.getRelationshipName());
        payload.setCascadeDelete(field.isCascadeDelete());
        return payload;
    }

    /**
     * Creates an AuthzChangedPayload from route and field policies.
     */
    public static AuthzChangedPayload toAuthzPayload(
            String collectionId,
            String collectionName,
            List<RoutePolicy> routePolicies,
            List<FieldPolicy> fieldPolicies) {
        
        AuthzChangedPayload payload = new AuthzChangedPayload();
        payload.setCollectionId(collectionId);
        payload.setCollectionName(collectionName);
        payload.setTimestamp(Instant.now());

        if (routePolicies != null) {
            payload.setRoutePolicies(routePolicies.stream()
                    .map(PayloadAdapter::toRoutePolicyPayload)
                    .collect(Collectors.toList()));
        }

        if (fieldPolicies != null) {
            payload.setFieldPolicies(fieldPolicies.stream()
                    .map(PayloadAdapter::toFieldPolicyPayload)
                    .collect(Collectors.toList()));
        }

        return payload;
    }

    /**
     * Creates a RoutePolicyPayload from a RoutePolicy entity.
     */
    private static AuthzChangedPayload.RoutePolicyPayload toRoutePolicyPayload(RoutePolicy routePolicy) {
        AuthzChangedPayload.RoutePolicyPayload payload = new AuthzChangedPayload.RoutePolicyPayload();
        payload.setId(routePolicy.getId());
        payload.setOperation(routePolicy.getOperation());
        if (routePolicy.getPolicy() != null) {
            payload.setPolicyId(routePolicy.getPolicy().getId());
            payload.setPolicyName(routePolicy.getPolicy().getName());
            payload.setPolicyRules(routePolicy.getPolicy().getRules());
        }
        return payload;
    }

    /**
     * Creates a FieldPolicyPayload from a FieldPolicy entity.
     */
    private static AuthzChangedPayload.FieldPolicyPayload toFieldPolicyPayload(FieldPolicy fieldPolicy) {
        AuthzChangedPayload.FieldPolicyPayload payload = new AuthzChangedPayload.FieldPolicyPayload();
        payload.setId(fieldPolicy.getId());
        payload.setOperation(fieldPolicy.getOperation());
        if (fieldPolicy.getField() != null) {
            payload.setFieldId(fieldPolicy.getField().getId());
            payload.setFieldName(fieldPolicy.getField().getName());
        }
        if (fieldPolicy.getPolicy() != null) {
            payload.setPolicyId(fieldPolicy.getPolicy().getId());
            payload.setPolicyName(fieldPolicy.getPolicy().getName());
            payload.setPolicyRules(fieldPolicy.getPolicy().getRules());
        }
        return payload;
    }

}
