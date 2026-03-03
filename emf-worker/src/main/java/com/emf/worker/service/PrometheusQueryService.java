package com.emf.worker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;

/**
 * Service for querying Prometheus via its HTTP API.
 *
 * <p>Provides methods for instant queries ({@code /api/v1/query}) and
 * range queries ({@code /api/v1/query_range}). Parses the Prometheus
 * JSON response into {@link TimeSeries} model objects.
 *
 * @since 1.0.0
 */
@Service
public class PrometheusQueryService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusQueryService.class);

    private final RestTemplate prometheusRestTemplate;
    private final ObjectMapper objectMapper;

    public PrometheusQueryService(RestTemplate prometheusRestTemplate, ObjectMapper objectMapper) {
        this.prometheusRestTemplate = prometheusRestTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes a range query against Prometheus.
     *
     * @param promql the PromQL expression
     * @param start  start time as ISO-8601 instant
     * @param end    end time as ISO-8601 instant
     * @param step   step duration (e.g., "60s", "5m")
     * @return list of time series results
     */
    public List<TimeSeries> queryRange(String promql, Instant start, Instant end, String step) {
        log.debug("Prometheus range query: promql={}, start={}, end={}, step={}", promql, start, end, step);

        String uri = UriComponentsBuilder.fromPath("/api/v1/query_range")
                .queryParam("query", promql)
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .queryParam("step", step)
                .build()
                .toUriString();

        try {
            String responseBody = prometheusRestTemplate.getForObject(uri, String.class);
            return parseResponse(responseBody);
        } catch (Exception e) {
            log.error("Prometheus range query failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Executes an instant query against Prometheus.
     *
     * @param promql the PromQL expression
     * @return list of time series results (usually single-point per series)
     */
    public List<TimeSeries> queryInstant(String promql) {
        log.debug("Prometheus instant query: promql={}", promql);

        String uri = UriComponentsBuilder.fromPath("/api/v1/query")
                .queryParam("query", promql)
                .build()
                .toUriString();

        try {
            String responseBody = prometheusRestTemplate.getForObject(uri, String.class);
            return parseResponse(responseBody);
        } catch (Exception e) {
            log.error("Prometheus instant query failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses a Prometheus JSON response into a list of time series.
     *
     * <p>Handles both "matrix" (range query) and "vector" (instant query) result types.
     */
    private List<TimeSeries> parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (!"success".equals(root.path("status").asText())) {
                log.warn("Prometheus query returned non-success status: {}",
                        root.path("error").asText("unknown"));
                return List.of();
            }

            JsonNode data = root.path("data");
            String resultType = data.path("resultType").asText();
            JsonNode results = data.path("result");

            List<TimeSeries> seriesList = new ArrayList<>();

            for (JsonNode result : results) {
                Map<String, String> labels = new LinkedHashMap<>();
                JsonNode metric = result.path("metric");
                metric.fieldNames().forEachRemaining(field ->
                        labels.put(field, metric.get(field).asText()));

                List<DataPoint> dataPoints = new ArrayList<>();

                if ("matrix".equals(resultType)) {
                    // Range query: "values" array of [timestamp, value]
                    for (JsonNode point : result.path("values")) {
                        double timestamp = point.get(0).asDouble();
                        String value = point.get(1).asText();
                        dataPoints.add(new DataPoint(timestamp, parseValue(value)));
                    }
                } else if ("vector".equals(resultType)) {
                    // Instant query: single "value" [timestamp, value]
                    JsonNode value = result.path("value");
                    if (!value.isMissingNode() && value.size() >= 2) {
                        double timestamp = value.get(0).asDouble();
                        String val = value.get(1).asText();
                        dataPoints.add(new DataPoint(timestamp, parseValue(val)));
                    }
                }

                seriesList.add(new TimeSeries(labels, dataPoints));
            }

            return seriesList;
        } catch (Exception e) {
            log.error("Failed to parse Prometheus response: {}", e.getMessage());
            return List.of();
        }
    }

    private double parseValue(String value) {
        if (value == null || "NaN".equals(value) || "+Inf".equals(value) || "-Inf".equals(value)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // =========================================================================
    // Inner record types
    // =========================================================================

    /**
     * A single time series with labels and data points.
     */
    public record TimeSeries(Map<String, String> labels, List<DataPoint> dataPoints) {}

    /**
     * A single data point with a timestamp (Unix epoch seconds) and a value.
     */
    public record DataPoint(double timestamp, double value) {}
}
