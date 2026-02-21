package com.emf.controlplane.service;

import com.emf.controlplane.dto.ObjectPermissions;
import com.emf.controlplane.entity.FieldVisibility;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.repository.UserRepository;
import com.emf.controlplane.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Central authorization service used by @PreAuthorize annotations.
 * Delegates to PermissionResolutionService for database-backed permission checks.
 * PLATFORM_ADMIN role bypasses all tenant-level permissions.
 */
@Service("securityService")
public class SecurityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);

    private final PermissionResolutionService permissionService;
    private final UserRepository userRepository;

    public SecurityService(PermissionResolutionService permissionService,
                           UserRepository userRepository) {
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    /**
     * Check if the current user has a system permission.
     * Called by @PreAuthorize("@securityService.hasPermission(#root, 'PERMISSION_NAME')")
     */
    public boolean hasPermission(MethodSecurityExpressionOperations root, String permission) {
        Authentication auth = root.getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        if (isPlatformAdmin(auth)) return true;

        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) return false;

        String userId = resolveUserId(auth, tenantId);
        if (userId == null) return false;

        return permissionService.hasSystemPermission(tenantId, userId, permission);
    }

    /**
     * Check if the current user has object-level permission on a collection.
     * Called by @PreAuthorize("@securityService.hasObjectPermission(#root, 'collectionId', 'READ')")
     */
    public boolean hasObjectPermission(MethodSecurityExpressionOperations root,
                                        String collectionId, String action) {
        Authentication auth = root.getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        if (isPlatformAdmin(auth)) return true;

        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) return false;

        String userId = resolveUserId(auth, tenantId);
        if (userId == null) return false;

        ObjectPermissions perms = permissionService.getObjectPermissions(tenantId, userId, collectionId);
        return switch (action.toUpperCase()) {
            case "CREATE" -> perms.canCreate();
            case "READ" -> perms.canRead();
            case "EDIT" -> perms.canEdit();
            case "DELETE" -> perms.canDelete();
            case "VIEW_ALL" -> perms.canViewAll();
            case "MODIFY_ALL" -> perms.canModifyAll();
            default -> false;
        };
    }

    /**
     * Check if the user has PLATFORM_ADMIN role (bypasses all tenant permissions).
     */
    public boolean isPlatformAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_PLATFORM_ADMIN"));
    }

    /**
     * Check if the current user is an admin (ADMIN or PLATFORM_ADMIN role).
     * Used for backward compatibility during migration from role-based to permission-based.
     */
    public boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_PLATFORM_ADMIN"));
    }

    /**
     * Resolves the platform_user ID from the JWT authentication.
     * The JWT principal name is the email address; we look up the user by email in the tenant.
     */
    private String resolveUserId(Authentication auth, String tenantId) {
        String email = auth.getName();
        return userRepository.findByTenantIdAndEmail(tenantId, email)
                .map(User::getId)
                .orElse(null);
    }
}
