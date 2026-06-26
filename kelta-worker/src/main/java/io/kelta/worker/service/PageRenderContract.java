package io.kelta.worker.service;

import java.util.List;
import java.util.Map;

/**
 * Versioned render contract for a published custom page. The end-user shell fetches this from
 * {@code GET /api/pages/{slug}/render} and maps {@code tree} (the builder's component tree) to
 * components. {@code version} lets the renderer evolve the node schema without breaking older
 * clients — it is pinned from day one.
 *
 * <p>{@code variables} and {@code dataSources} are the page-level config (page state declarations and
 * on-load query declarations) surfaced verbatim from the stored {@code config}. They are opaque
 * pass-through JSON — the server never resolves a {@code $bind} expression or executes a data source
 * (doing so would fetch records outside the authorized JSON:API path and bypass Cerbos/FLS). The
 * client resolves bindings and fetches data sources over the authorized API. {@code tree} carries the
 * whole {@code config} map verbatim, so {@code tree.components} resolves exactly as before.
 */
public record PageRenderContract(
        String version,
        String slug,
        String title,
        String path,
        List<Map<String, Object>> variables,
        List<Map<String, Object>> dataSources,
        Map<String, Object> tree
) {
}
