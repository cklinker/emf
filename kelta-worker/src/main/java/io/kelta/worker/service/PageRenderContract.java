package io.kelta.worker.service;

import java.util.Map;

/**
 * Versioned render contract for a published custom page. The end-user shell fetches this from
 * {@code GET /api/pages/{slug}/render} and maps {@code tree} (the builder's component tree) to
 * components. {@code version} lets the renderer evolve the node schema without breaking older
 * clients — it is pinned from day one.
 */
public record PageRenderContract(
        String version,
        String slug,
        String title,
        String path,
        Map<String, Object> tree
) {
}
