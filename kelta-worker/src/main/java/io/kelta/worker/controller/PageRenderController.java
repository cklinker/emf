package io.kelta.worker.controller;

import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.PageRenderContract;
import io.kelta.worker.service.PageRenderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves published custom pages as a versioned render contract. Tenant context is bound per request
 * by {@code TenantContextFilter}; the service only ever returns published+active pages, so this is
 * safe for end-user (non-admin) consumption. The new {@code /api/pages/**} segment is registered as
 * a gateway static route.
 *
 * <p>For pages that declare {@code config.access.requiredPermission} (slice 1h), the caller's profile
 * id (gateway-forwarded {@code X-User-Profile-Id}) is resolved and passed to the service, which gates
 * the render on that system permission. A denial flows through the same {@code Optional.empty() → 404}
 * branch as an unknown slug, so a restricted page's existence is never leaked.
 */
@RestController
@RequestMapping("/api/pages")
public class PageRenderController {

    private final PageRenderService pageRenderService;
    private final CerbosPermissionResolver permissionResolver;

    public PageRenderController(PageRenderService pageRenderService,
                                CerbosPermissionResolver permissionResolver) {
        this.pageRenderService = pageRenderService;
        this.permissionResolver = permissionResolver;
    }

    @GetMapping("/{slug}/render")
    public ResponseEntity<PageRenderContract> render(@PathVariable String slug,
                                                     HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        return pageRenderService.render(slug, profileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
