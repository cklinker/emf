package io.kelta.worker.controller;

import io.kelta.worker.service.PageRenderContract;
import io.kelta.worker.service.PageRenderService;
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
 */
@RestController
@RequestMapping("/api/pages")
public class PageRenderController {

    private final PageRenderService pageRenderService;

    public PageRenderController(PageRenderService pageRenderService) {
        this.pageRenderService = pageRenderService;
    }

    @GetMapping("/{slug}/render")
    public ResponseEntity<PageRenderContract> render(@PathVariable String slug) {
        return pageRenderService.render(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
