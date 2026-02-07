package com.emf.controlplane.audit;

import com.emf.controlplane.entity.BaseEntity;
import com.emf.controlplane.service.SetupAuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that automatically captures before/after state for
 * annotated service methods and logs to the setup audit trail.
 */
@Aspect
@Component
public class SetupAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(SetupAuditAspect.class);

    private final SetupAuditService setupAuditService;

    public SetupAuditAspect(SetupAuditService setupAuditService) {
        this.setupAuditService = setupAuditService;
    }

    @Around("@annotation(audited)")
    public Object auditSetupChange(ProceedingJoinPoint joinPoint, SetupAudited audited)
            throws Throwable {

        String action = audited.action().isEmpty()
                ? detectAction(joinPoint.getSignature().getName())
                : audited.action();

        // Execute the actual method
        Object result = joinPoint.proceed();

        // Extract info from result and log audit entry
        try {
            String entityId = extractEntityId(result);
            String entityName = extractEntityName(result, joinPoint.getArgs());

            // For CREATED/UPDATED, the result is the new state
            Object newValue = "DELETED".equals(action) ? null : result;

            // Main's SetupAuditService.log() auto-extracts tenantId and userId
            // from TenantContextHolder and SecurityContextHolder
            setupAuditService.log(
                    action,
                    audited.section(),
                    audited.entityType(),
                    entityId,
                    entityName,
                    null, // old value (future: capture before state)
                    newValue
            );
        } catch (Exception e) {
            log.warn("Failed to record setup audit for {}.{}: {}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    e.getMessage());
        }

        return result;
    }

    private String detectAction(String methodName) {
        if (methodName.startsWith("create") || methodName.startsWith("add")) return "CREATED";
        if (methodName.startsWith("update") || methodName.startsWith("set")) return "UPDATED";
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) return "DELETED";
        if (methodName.startsWith("activate")) return "ACTIVATED";
        if (methodName.startsWith("deactivate")) return "DEACTIVATED";
        return "MODIFIED";
    }

    private String extractEntityId(Object result) {
        if (result instanceof BaseEntity entity) {
            return entity.getId();
        }
        return null;
    }

    private String extractEntityName(Object result, Object[] args) {
        // Try to get name from result using reflection
        if (result != null) {
            try {
                var getNameMethod = result.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(result);
                if (name != null) {
                    return name.toString();
                }
            } catch (NoSuchMethodException e) {
                // No getName method, try other approaches
            } catch (Exception e) {
                log.trace("Could not extract entity name from result: {}", e.getMessage());
            }
        }

        // Try first string argument as a fallback
        for (Object arg : args) {
            if (arg instanceof String s && !s.isEmpty() && s.length() <= 200) {
                return s;
            }
        }
        return null;
    }
}
