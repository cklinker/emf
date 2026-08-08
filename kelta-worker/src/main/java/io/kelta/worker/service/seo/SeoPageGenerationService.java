package io.kelta.worker.service.seo;

import io.kelta.worker.repository.SeoPageRepository;
import io.kelta.worker.repository.SeoPageRepository.SeoPageRow;
import io.kelta.worker.repository.SeoPageRepository.TargetAggregate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Generates the {@code seo_page} corpus (consumer-alerting slice 11): one per-target stat block,
 * refreshed on a schedule (nightly by default), that a static content site renders at build time.
 *
 * <p>Runs unscoped on the scheduler thread (no tenant bound → {@code admin_bypass}), so one pass
 * reads every tenant's targets/watches/wins and upserts each tenant's pages. Deliberately writes
 * only AGGREGATE data (watcher/win counts + target metadata) — never member data.
 *
 * <p><b>§6.3 quality guardrail.</b> A page is only {@code published} when it is backed by enough
 * real data ({@code watcherCount ≥ min} OR any wins); otherwise it stays unpublished so a static
 * build can noindex it rather than shipping thin programmatic spam. Pages whose target went away
 * (or whose name changed, producing a new slug) are pruned each cycle.
 */
@Service
public class SeoPageGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SeoPageGenerationService.class);

    private static final int MAX_SLUG_BASE = 180;
    private static final int TARGET_SUFFIX_LEN = 8;

    private final SeoPageRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int minWatchers;
    private final Counter generatedCounter;

    public SeoPageGenerationService(
            SeoPageRepository repository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${kelta.seo.generation.enabled:true}") boolean enabled,
            @Value("${kelta.seo.generation.min-watchers:5}") int minWatchers) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.minWatchers = minWatchers;
        this.generatedCounter = Counter.builder("kelta_worker_seo_pages_generated")
                .description("seo_page rows upserted by the generation sweep")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${kelta.seo.generation.poll-interval-ms:86400000}")
    public void generate() {
        if (!enabled) {
            return;
        }
        Instant runStart = Instant.now();
        try {
            List<TargetAggregate> aggregates = repository.computeTargetAggregates();
            int total = 0;
            int published = 0;
            for (TargetAggregate agg : aggregates) {
                boolean pub = isPublishable(agg);
                repository.upsert(new SeoPageRow(
                        UUID.randomUUID().toString(),
                        agg.tenantId(),
                        agg.targetId(),
                        slug(agg.name(), agg.targetId()),
                        title(agg.name()),
                        agg.category(),
                        agg.watcherCount(),
                        agg.winCount(),
                        agg.lastWinAt(),
                        statsJson(agg, pub),
                        pub,
                        runStart));
                total++;
                if (pub) {
                    published++;
                }
            }
            int pruned = repository.deleteGeneratedBefore(runStart);
            generatedCounter.increment(total);
            log.info("SEO generation: {} page(s) upserted ({} published), {} stale pruned",
                    total, published, pruned);
        } catch (Exception e) {
            log.error("SEO page generation failed: {}", e.getMessage(), e);
        }
    }

    /** Guardrail: enough watchers, or at least one win, to be worth a public page. */
    boolean isPublishable(TargetAggregate agg) {
        return agg.watcherCount() >= minWatchers || agg.winCount() > 0;
    }

    /**
     * A human-readable, URL-safe slug from the target name plus a short target-id suffix that
     * keeps two similarly-named targets distinct. Deterministic — the same target always upserts
     * the same slug.
     */
    static String slug(String name, String targetId) {
        String base = (name == null ? "" : name).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "target";
        }
        if (base.length() > MAX_SLUG_BASE) {
            base = base.substring(0, MAX_SLUG_BASE).replaceAll("-+$", "");
        }
        String suffix = "";
        if (targetId != null) {
            String hex = targetId.replaceAll("[^a-zA-Z0-9]", "");
            if (!hex.isEmpty()) {
                suffix = "-" + hex.substring(0, Math.min(TARGET_SUFFIX_LEN, hex.length()));
            }
        }
        return base + suffix;
    }

    private static String title(String name) {
        return (name == null || name.isBlank()) ? "Untitled" : name;
    }

    private String statsJson(TargetAggregate agg, boolean published) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("watcherCount", agg.watcherCount());
        stats.put("winCount", agg.winCount());
        if (agg.lastWinAt() != null) {
            stats.put("lastWinAt", agg.lastWinAt().toString());
        }
        stats.put("published", published);
        try {
            return objectMapper.writeValueAsString(stats);
        } catch (RuntimeException e) {
            // Jackson 3 throws unchecked; a stat-serialization hiccup must not fail the sweep.
            return "{}";
        }
    }
}
