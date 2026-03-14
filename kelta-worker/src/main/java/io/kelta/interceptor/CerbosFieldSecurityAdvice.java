package io.kelta.worker.interceptor;

import io.kelta.worker.service.CerbosAuthorizationService;
import io.kelta.worker.service.CerbosPermissionResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.*;

/**
 * Strips fields from API responses based on Cerbos field-level security.
 *
 * <p>For each field in each record, checks Cerbos for "read" access.
 * Fields denied by Cerbos are removed from the response attributes.
 *
 * <p>Runs after {@link CerbosRecordAuthorizationAdvice} (higher order = later).
 */
@ControllerAdvice
@Order(10) // After CerbosRecordAuthorizationAdvice (default order 0)
public class CerbosFieldSecurityAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(CerbosFieldSecurityAdvice.class);

    private final CerbosAuthorizationService authzService;
    private final CerbosPermissionResolver permissionResolver;
    private final boolean permissionsEnabled;

    public CerbosFieldSecurityAdvice(
            CerbosAuthorizationService authzService,
            CerbosPermissionResolver permissionResolver,
            @Value("${kelta.gateway.security.permissions-enabled:true}") boolean permissionsEnabled) {
        this.authzService = authzService;
        this.permissionResolver = permissionResolver;
        this.permissionsEnabled = permissionsEnabled;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return permissionsEnabled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/") || path.startsWith("/api/admin/") || path.startsWith("/api/me/")) {
            return body;
        }

        if (!permissionResolver.hasIdentity(httpRequest)) {
            return body;
        }

        if (!(body instanceof Map)) {
            return body;
        }

        Map<String, Object> responseBody = (Map<String, Object>) body;
        Object data = responseBody.get("data");
        if (data == null) {
            return body;
        }

        String email = permissionResolver.getEmail(httpRequest);
        String profileId = permissionResolver.getProfileId(httpRequest);
        String tenantId = permissionResolver.getTenantId(httpRequest);
        String collectionId = extractCollectionId(path);

        if (data instanceof List<?> records) {
            for (Object record : records) {
                if (record instanceof Map<?, ?> recordMap) {
                    stripHiddenFields(email, profileId, tenantId, collectionId,
                            (Map<String, Object>) recordMap);
                }
            }
        } else if (data instanceof Map<?, ?> singleRecord) {
            stripHiddenFields(email, profileId, tenantId, collectionId,
                    (Map<String, Object>) singleRecord);
        }

        // Also process included resources
        Object included = responseBody.get("included");
        if (included instanceof List<?> includedList) {
            for (Object item : includedList) {
                if (item instanceof Map<?, ?> includedRecord) {
                    Map<String, Object> typedIncluded = (Map<String, Object>) includedRecord;
                    String includedType = (String) typedIncluded.get("type");
                    if (includedType != null) {
                        stripHiddenFields(email, profileId, tenantId, includedType, typedIncluded);
                    }
                }
            }
        }

        return responseBody;
    }

    @SuppressWarnings("unchecked")
    private void stripHiddenFields(String email, String profileId, String tenantId,
                                    String collectionId, Map<String, Object> record) {
        Object attrObj = record.get("attributes");
        if (!(attrObj instanceof Map<?, ?> attrMap)) {
            return;
        }

        Map<String, Object> attributes = (Map<String, Object>) attrMap;
        Set<String> fieldsToRemove = new HashSet<>();

        for (String fieldId : attributes.keySet()) {
            // Skip system fields (createdAt, updatedAt, etc.)
            if (isSystemField(fieldId)) {
                continue;
            }
            if (!authzService.checkFieldAccess(email, profileId, tenantId,
                    collectionId, fieldId, "read")) {
                fieldsToRemove.add(fieldId);
            }
        }

        if (!fieldsToRemove.isEmpty()) {
            log.debug("Stripping {} hidden fields from {} for user={}",
                    fieldsToRemove.size(), collectionId, email);
            for (String field : fieldsToRemove) {
                attributes.remove(field);
            }
        }
    }

    private boolean isSystemField(String fieldId) {
        return "createdAt".equals(fieldId) || "updatedAt".equals(fieldId)
                || "createdBy".equals(fieldId) || "updatedBy".equals(fieldId);
    }

    private String extractCollectionId(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "";
    }
}
