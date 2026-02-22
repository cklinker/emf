package com.emf.controlplane.service;

import com.emf.controlplane.dto.ObjectPermissions;
import com.emf.controlplane.entity.FieldVisibility;
import com.emf.controlplane.entity.User;
import com.emf.controlplane.repository.FileAttachmentRepository;
import com.emf.controlplane.repository.NoteRepository;
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
    private final NoteRepository noteRepository;
    private final FileAttachmentRepository attachmentRepository;
    private final SecurityAuditService auditService;

    public SecurityService(PermissionResolutionService permissionService,
                           UserRepository userRepository,
                           NoteRepository noteRepository,
                           FileAttachmentRepository attachmentRepository,
                           @org.springframework.lang.Nullable SecurityAuditService auditService) {
        this.permissionService = permissionService;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.attachmentRepository = attachmentRepository;
        this.auditService = auditService;
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

        boolean granted = permissionService.hasSystemPermission(tenantId, userId, permission);
        if (!granted && auditService != null) {
            auditService.logPermissionDenied(null, null, permission);
        }
        return granted;
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
        boolean granted = switch (action.toUpperCase()) {
            case "CREATE" -> perms.canCreate();
            case "READ" -> perms.canRead();
            case "EDIT" -> perms.canEdit();
            case "DELETE" -> perms.canDelete();
            case "VIEW_ALL" -> perms.canViewAll();
            case "MODIFY_ALL" -> perms.canModifyAll();
            default -> false;
        };
        if (!granted && auditService != null) {
            auditService.logPermissionDenied(collectionId, action, "OBJECT_" + action.toUpperCase());
        }
        return granted;
    }

    /**
     * Check if the current user has permission on a note's parent collection.
     * Looks up the note to resolve its collectionId, then delegates to hasObjectPermission.
     *
     * @param root   the method security expression operations (injected by SpEL)
     * @param noteId the note UUID
     * @param action the permission action (e.g. "EDIT")
     * @return true if the user has the requested permission on the note's collection
     */
    public boolean hasNotePermission(MethodSecurityExpressionOperations root, String noteId, String action) {
        return noteRepository.findById(noteId)
                .map(note -> hasObjectPermission(root, note.getCollectionId(), action))
                .orElse(false);
    }

    /**
     * Check if the current user has permission on an attachment's parent collection.
     * Looks up the attachment to resolve its collectionId, then delegates to hasObjectPermission.
     *
     * @param root         the method security expression operations (injected by SpEL)
     * @param attachmentId the attachment UUID
     * @param action       the permission action (e.g. "EDIT")
     * @return true if the user has the requested permission on the attachment's collection
     */
    public boolean hasAttachmentPermission(MethodSecurityExpressionOperations root, String attachmentId, String action) {
        return attachmentRepository.findById(attachmentId)
                .map(attachment -> hasObjectPermission(root, attachment.getCollectionId(), action))
                .orElse(false);
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
