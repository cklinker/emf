package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.service.ReportExecutionService;
import io.kelta.worker.service.ReportExecutionService.ReportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * REST controller for executing reports and exporting report data.
 *
 * <p>Provides endpoints to run a report (producing paginated query results
 * from the report's configuration) and to export report data as CSV.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/reports")
public class ReportExecutionController {

    private static final Logger log = LoggerFactory.getLogger(ReportExecutionController.class);

    private final ReportExecutionService reportExecutionService;
    private final QueryEngine queryEngine;
    private final CollectionRegistry collectionRegistry;

    public ReportExecutionController(ReportExecutionService reportExecutionService,
                                     QueryEngine queryEngine,
                                     CollectionRegistry collectionRegistry) {
        this.reportExecutionService = reportExecutionService;
        this.queryEngine = queryEngine;
        this.collectionRegistry = collectionRegistry;
    }

    /**
     * Executes a report and returns paginated results.
     *
     * @param reportId   the report ID
     * @param pageNumber the page number (1-indexed, default 1)
     * @param pageSize   the page size (default 200, max 2000)
     * @return paginated report data with metadata
     */
    @PostMapping("/{reportId}/execute")
    public ResponseEntity<Map<String, Object>> executeReport(
            @PathVariable String reportId,
            @RequestParam(value = "page[number]", defaultValue = "1") int pageNumber,
            @RequestParam(value = "page[size]", defaultValue = "200") int pageSize) {

        try {
            // Load the report config from the reports system collection
            Map<String, Object> reportConfig = loadReportConfig(reportId);
            if (reportConfig == null) {
                return ResponseEntity.notFound().build();
            }

            ReportResult result = reportExecutionService.execute(reportConfig, pageNumber, pageSize);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("reportId", reportId);
            attributes.put("reportName", reportConfig.get("name"));
            attributes.put("reportType", reportConfig.get("reportType"));
            attributes.put("columns", result.columns().stream()
                .map(c -> {
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("fieldName", c.fieldName());
                    col.put("label", c.label());
                    col.put("type", c.type());
                    return col;
                })
                .toList());
            attributes.put("records", result.data());

            if (result.groups() != null) {
                attributes.put("groups", result.groups());
                attributes.put("groupAggregations", result.groupAggregations());
            }

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("totalCount", result.metadata().totalCount());
            meta.put("currentPage", result.metadata().currentPage());
            meta.put("pageSize", result.metadata().pageSize());
            meta.put("totalPages", result.metadata().totalPages());

            return ResponseEntity.ok(
                JsonApiResponseBuilder.single("report-results", reportId, attributes, meta));

        } catch (ReportExecutionService.ReportExecutionException e) {
            log.warn("Report execution failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                JsonApiResponseBuilder.error("400", "Bad Request", e.getMessage()));
        } catch (Exception e) {
            log.error("Report execution error: reportId={}", reportId, e);
            return ResponseEntity.internalServerError().body(
                JsonApiResponseBuilder.error("500", "Internal Server Error",
                    "Failed to execute report"));
        }
    }

    /**
     * Exports report data as CSV.
     *
     * @param reportId the report ID
     * @param response the HTTP response for streaming
     */
    @GetMapping("/{reportId}/export")
    public void exportReport(
            @PathVariable String reportId,
            @RequestParam(defaultValue = "csv") String format,
            HttpServletResponse response) {

        try {
            Map<String, Object> reportConfig = loadReportConfig(reportId);
            if (reportConfig == null) {
                response.setStatus(404);
                return;
            }

            String reportName = (String) reportConfig.getOrDefault("name", "report");
            String safeFileName = reportName.replaceAll("[^a-zA-Z0-9._-]", "_");

            if ("csv".equalsIgnoreCase(format)) {
                response.setContentType("text/csv; charset=UTF-8");
                response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + safeFileName + ".csv\"");

                try (Writer writer = new OutputStreamWriter(
                        response.getOutputStream(), StandardCharsets.UTF_8)) {
                    reportExecutionService.exportCsv(reportConfig, writer);
                    writer.flush();
                }
            } else {
                response.setStatus(400);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    "{\"errors\":[{\"status\":\"400\",\"title\":\"Bad Request\","
                    + "\"detail\":\"Unsupported export format: " + format + ". Supported: csv\"}]}");
            }

        } catch (ReportExecutionService.ReportExecutionException e) {
            log.warn("Report export failed: {}", e.getMessage());
            try {
                response.setStatus(400);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    "{\"errors\":[{\"status\":\"400\",\"title\":\"Bad Request\","
                    + "\"detail\":\"" + e.getMessage().replace("\"", "'") + "\"}]}");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("Report export error: reportId={}", reportId, e);
            try {
                response.setStatus(500);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Loads a report configuration from the reports system collection.
     */
    private Map<String, Object> loadReportConfig(String reportId) {
        CollectionDefinition reportsDef = collectionRegistry.get("reports");
        if (reportsDef == null) {
            log.error("Reports system collection not found in registry");
            return null;
        }

        return queryEngine.getById(reportsDef, reportId).orElse(null);
    }
}
