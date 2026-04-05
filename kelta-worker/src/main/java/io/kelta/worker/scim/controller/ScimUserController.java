package io.kelta.worker.scim.controller;

import io.kelta.worker.scim.ScimConstants;
import io.kelta.worker.scim.model.ScimListResponse;
import io.kelta.worker.scim.model.ScimPatchOp;
import io.kelta.worker.scim.model.ScimUser;
import io.kelta.worker.scim.service.ScimUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scim/v2/Users")
public class ScimUserController {

    private final ScimUserService scimUserService;

    public ScimUserController(ScimUserService scimUserService) {
        this.scimUserService = scimUserService;
    }

    @GetMapping(produces = ScimConstants.CONTENT_TYPE_SCIM)
    public ResponseEntity<ScimListResponse<ScimUser>> listUsers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "startIndex", defaultValue = "1") int startIndex,
            @RequestParam(value = "count", defaultValue = "100") int count,
            HttpServletRequest request) {
        count = Math.min(count, ScimConstants.MAX_PAGE_SIZE);
        String baseUrl = getBaseUrl(request);
        return ResponseEntity.ok(scimUserService.listUsers(tenantId, filter, startIndex, count, baseUrl));
    }

    @GetMapping(value = "/{id}", produces = ScimConstants.CONTENT_TYPE_SCIM)
    public ResponseEntity<ScimUser> getUser(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String id,
            HttpServletRequest request) {
        return ResponseEntity.ok(scimUserService.getUser(tenantId, id, getBaseUrl(request)));
    }

    @PostMapping(produces = ScimConstants.CONTENT_TYPE_SCIM, consumes = {ScimConstants.CONTENT_TYPE_SCIM, "application/json"})
    public ResponseEntity<ScimUser> createUser(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody ScimUser user,
            HttpServletRequest request) {
        ScimUser created = scimUserService.createUser(tenantId, user, getBaseUrl(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/{id}", produces = ScimConstants.CONTENT_TYPE_SCIM, consumes = {ScimConstants.CONTENT_TYPE_SCIM, "application/json"})
    public ResponseEntity<ScimUser> replaceUser(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String id,
            @RequestBody ScimUser user,
            HttpServletRequest request) {
        return ResponseEntity.ok(scimUserService.replaceUser(tenantId, id, user, getBaseUrl(request)));
    }

    @PatchMapping(value = "/{id}", produces = ScimConstants.CONTENT_TYPE_SCIM, consumes = {ScimConstants.CONTENT_TYPE_SCIM, "application/json"})
    public ResponseEntity<ScimUser> patchUser(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String id,
            @RequestBody ScimPatchOp patchOp,
            HttpServletRequest request) {
        return ResponseEntity.ok(scimUserService.patchUser(tenantId, id, patchOp, getBaseUrl(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable String id) {
        scimUserService.deleteUser(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null) scheme = request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null) host = request.getServerName() + ":" + request.getServerPort();
        return scheme + "://" + host;
    }
}
