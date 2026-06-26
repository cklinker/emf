package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.worker.service.ChangesService;
import io.kelta.worker.service.ChangesService.ChangesException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline-sync changes feed for a collection: {@code GET /api/{collection}/_changes?since=<ISO-8601>}.
 * Returns the record deletions since the cursor + a fresh cursor (see {@link ChangesService}). The
 * literal {@code _changes} segment is more specific than the dynamic {@code /{collection}/{id}}
 * route and falls under the collection's existing gateway route — no new static route needed.
 */
@RestController
@RequestMapping("/api/{collection}/_changes")
public class ChangesController {

    private final ChangesService changesService;

    public ChangesController(ChangesService changesService) {
        this.changesService = changesService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> changes(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable("collection") String collection,
            @RequestParam(value = "since", required = false) String since) {

        Instant sinceInstant = null;
        if (since != null && !since.isBlank()) {
            try {
                sinceInstant = Instant.parse(since.trim());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(JsonApiResponseBuilder.error(
                        "400", "INVALID_SINCE", "Invalid 'since' cursor",
                        "'since' must be an ISO-8601 instant, e.g. 2026-06-21T00:00:00Z"));
            }
        }

        try {
            Map<String, Object> data = changesService.changes(tenantId, collection, sinceInstant);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (ChangesException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
