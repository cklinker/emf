package com.emf.controlplane.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for service methods that should be automatically audited
 * in the setup audit trail. The AOP aspect captures before/after state
 * and logs to SetupAuditService.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SetupAudited {

    /**
     * The setup section, e.g., "Collections", "Fields", "Picklists".
     */
    String section();

    /**
     * The entity type, e.g., "Field", "ValidationRule", "RecordType".
     */
    String entityType();

    /**
     * The action. If empty, auto-detected from method name prefix:
     * create* → CREATED, update/set* → UPDATED, delete/remove* → DELETED,
     * activate* → ACTIVATED, deactivate* → DEACTIVATED.
     */
    String action() default "";
}
