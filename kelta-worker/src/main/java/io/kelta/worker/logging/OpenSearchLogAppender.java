package io.kelta.worker.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Logback appender that batches log events and writes them to OpenSearch.
 * Configured in logback-spring.xml. Uses a simple HTTP client to avoid
 * circular dependency with Spring beans.
 */
public class OpenSearchLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter INDEX_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    private String opensearchUrl = "http://localhost:9200";
    private String indexPrefix = "kelta-logs";
    private String serviceName = "kelta-worker";
    private int batchSize = 50;
    private int flushIntervalMs = 5000;

    private final ConcurrentLinkedQueue<ILoggingEvent> buffer = new ConcurrentLinkedQueue<>();
    private HttpClient httpClient;
    private ScheduledExecutorService scheduler;

    // Setters for logback XML configuration
    public void setOpensearchUrl(String url) { this.opensearchUrl = url; }
    public void setIndexPrefix(String prefix) { this.indexPrefix = prefix; }
    public void setServiceName(String name) { this.serviceName = name; }
    public void setBatchSize(int size) { this.batchSize = size; }
    public void setFlushIntervalMs(int ms) { this.flushIntervalMs = ms; }

    @Override
    public void start() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "opensearch-log-appender");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        super.start();
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flush(); // Final flush
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Skip our own log messages to prevent infinite recursion
        if (event.getLoggerName().startsWith("io.kelta.worker.logging.OpenSearchLogAppender")) {
            return;
        }
        buffer.add(event);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    private void flush() {
        List<ILoggingEvent> events = new ArrayList<>();
        ILoggingEvent event;
        while ((event = buffer.poll()) != null && events.size() < batchSize * 2) {
            events.add(event);
        }
        if (events.isEmpty()) return;

        try {
            String index = indexPrefix + "-" + INDEX_DATE_FORMAT.format(Instant.now());
            StringBuilder bulk = new StringBuilder();
            for (ILoggingEvent e : events) {
                bulk.append("{\"index\":{\"_index\":\"").append(index).append("\"}}\n");
                bulk.append(toJson(e)).append("\n");
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(opensearchUrl + "/_bulk"))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(bulk.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // Can't log here — would cause infinite recursion
        }
    }

    private String toJson(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{");
        sb.append("\"@timestamp\":\"").append(Instant.ofEpochMilli(event.getTimeStamp()).toString()).append("\"");
        sb.append(",\"level\":\"").append(event.getLevel().toString()).append("\"");
        sb.append(",\"logger\":\"").append(escape(event.getLoggerName())).append("\"");
        sb.append(",\"message\":\"").append(escape(event.getFormattedMessage())).append("\"");
        sb.append(",\"thread\":\"").append(escape(event.getThreadName())).append("\"");
        sb.append(",\"service\":\"").append(serviceName).append("\"");

        // MDC fields (traceId, spanId injected by OTEL agent)
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null && !mdc.isEmpty()) {
            for (Map.Entry<String, String> entry : mdc.entrySet()) {
                sb.append(",\"").append(escape(entry.getKey())).append("\":\"")
                        .append(escape(entry.getValue())).append("\"");
            }
        }

        // Stack trace
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            String stackTrace = ThrowableProxyUtil.asString(throwable);
            sb.append(",\"stack_trace\":\"").append(escape(stackTrace)).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
