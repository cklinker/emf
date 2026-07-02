package io.kelta.worker.service.campaign;

import io.kelta.runtime.model.CollectionDefinition;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.runtime.query.FilterCondition;
import io.kelta.runtime.query.FilterOperator;
import io.kelta.runtime.query.Pagination;
import io.kelta.runtime.query.QueryRequest;
import io.kelta.runtime.query.QueryResult;
import io.kelta.runtime.query.QueryEngine;
import io.kelta.runtime.registry.CollectionRegistry;
import io.kelta.worker.config.CampaignProperties;
import io.kelta.worker.repository.CampaignRecipientRepository;
import io.kelta.worker.repository.CampaignRepository;
import io.kelta.worker.repository.EmailSuppressionRepository;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import io.kelta.worker.util.TenantContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes queued mass-email campaigns.
 *
 * <p>Polled by {@code CampaignPollerConfig}. Each cycle claims a batch of runnable campaigns
 * with a conditional-claim {@code UPDATE} (leader election across pods), then for each claimed
 * campaign resolves the recipient set from the target collection via the {@link QueryEngine}
 * (paged, reusing the export paging idiom), renders the template per recipient, sends through
 * the existing {@link EmailService} SMTP path, and records per-recipient send + tracking rows.
 *
 * <p>Spam controls: the per-tenant unsubscribe/suppression list is checked before every send,
 * the daily {@code campaignEmailsPerDay} governor limit caps total volume, and a configurable
 * per-second throttle paces delivery.
 *
 * @since 1.0.0
 */
@Service
public class CampaignRunnerService {

    private static final Logger log = LoggerFactory.getLogger(CampaignRunnerService.class);

    /** Internal paging size for recipient resolution (mirrors DataExportService). */
    private static final int PAGE_SIZE = 500;

    /** Rewrites {@code href="http(s)://..."} so link clicks route through the tracking redirect. */
    private static final Pattern HREF = Pattern.compile("href\\s*=\\s*\"(https?://[^\"]+)\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final EmailSuppressionRepository suppressionRepository;
    private final CollectionRegistry collectionRegistry;
    private final QueryEngine queryEngine;
    private final EmailService emailService;
    private final TenantQuotaResolver quotaResolver;
    private final TrackingTokenService tokenService;
    private final CampaignProperties properties;
    private final ObjectMapper objectMapper;

    public CampaignRunnerService(CampaignRepository campaignRepository,
                                 CampaignRecipientRepository recipientRepository,
                                 EmailSuppressionRepository suppressionRepository,
                                 CollectionRegistry collectionRegistry,
                                 QueryEngine queryEngine,
                                 EmailService emailService,
                                 TenantQuotaResolver quotaResolver,
                                 TrackingTokenService tokenService,
                                 CampaignProperties properties,
                                 ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.suppressionRepository = suppressionRepository;
        this.collectionRegistry = collectionRegistry;
        this.queryEngine = queryEngine;
        this.emailService = emailService;
        this.quotaResolver = quotaResolver;
        this.tokenService = tokenService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Claims and runs all currently-runnable campaigns. Called once per poll cycle.
     * Runs with no tenant in context (admin_bypass) so it can see campaigns across tenants;
     * each campaign's work then runs bound to that campaign's tenant.
     */
    public void processClaimableCampaigns() {
        List<Map<String, Object>> candidates = campaignRepository.findClaimable(properties.batchSize());
        for (Map<String, Object> candidate : candidates) {
            String campaignId = (String) candidate.get("id");
            String tenantId = (String) candidate.get("tenant_id");
            if (!campaignRepository.claim(campaignId)) {
                continue; // another pod won the claim
            }
            try {
                TenantContextUtils.withTenant(tenantId, () -> runCampaign(campaignId, tenantId));
            } catch (Exception e) {
                log.error("Campaign {} failed: {}", campaignId, e.getMessage(), e);
                campaignRepository.markFailed(campaignId, e.getMessage());
            }
        }
    }

    private void runCampaign(String campaignId, String tenantId) {
        Map<String, Object> campaign = campaignRepository.findById(campaignId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Campaign vanished: " + campaignId));

        String targetCollection = (String) campaign.get("target_collection");
        CollectionDefinition definition = collectionRegistry.get(targetCollection);
        if (definition == null) {
            campaignRepository.markFailed(campaignId, "Unknown target collection: " + targetCollection);
            return;
        }

        String emailField = (String) campaign.get("recipient_email_field");
        String subjectTemplate = (String) campaign.get("subject");
        String bodyTemplate = resolveBody(campaign);
        List<FilterCondition> filters = parseFilters(campaign.get("filter_json"));

        int dailyQuota = resolveDailyQuota(tenantId);
        int remaining = Math.max(0, dailyQuota - recipientRepository.countSentToday(tenantId));
        long pacingMs = properties.sendRatePerSecond() > 0 ? 1000L / properties.sendRatePerSecond() : 0L;

        int resolved = 0;
        int page = 1;
        while (true) {
            QueryRequest request = new QueryRequest(
                    new Pagination(page, PAGE_SIZE), List.of(), List.of(), filters);
            QueryResult result = queryEngine.executeQuery(definition, request);
            List<Map<String, Object>> rows = result.data();
            if (rows.isEmpty()) {
                break;
            }

            for (Map<String, Object> record : rows) {
                String email = stringValue(record.get(emailField));
                if (email == null || !EMAIL.matcher(email).matches()) {
                    continue; // no usable address — nothing to track or send
                }
                resolved++;

                Optional<String> recipientIdOpt =
                        recipientRepository.insertPending(tenantId, campaignId,
                                stringValue(record.get("id")), email);
                if (recipientIdOpt.isEmpty()) {
                    continue; // duplicate address within this campaign
                }
                String recipientId = recipientIdOpt.get();

                if (suppressionRepository.isSuppressed(tenantId, email)) {
                    recipientRepository.markStatus(recipientId, "SUPPRESSED");
                    continue;
                }
                if (remaining <= 0) {
                    recipientRepository.markStatus(recipientId, "SKIPPED");
                    continue; // daily send governor exhausted
                }

                sendOne(campaignId, tenantId, recipientId, email, subjectTemplate, bodyTemplate, record);
                remaining--;
                pace(pacingMs);
            }

            if (rows.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }

        campaignRepository.setTotalRecipients(campaignId, resolved);
        campaignRepository.markSent(campaignId);
        log.info("Campaign {} completed: {} recipients resolved", campaignId, resolved);
    }

    private void sendOne(String campaignId, String tenantId, String recipientId, String email,
                         String subjectTemplate, String bodyTemplate, Map<String, Object> record) {
        try {
            String token = tokenService.sign(recipientId);
            String unsubscribeUrl = link("/api/track/unsubscribe", token);

            Map<String, Object> vars = new java.util.HashMap<>(record);
            vars.put("unsubscribeUrl", unsubscribeUrl);
            vars.put("email", email);

            String subject = MergeFieldRenderer.render(subjectTemplate, vars);
            String body = MergeFieldRenderer.render(bodyTemplate, vars);
            body = injectTracking(body, token, unsubscribeUrl);

            String logId = emailService.queueEmail(tenantId, email, subject, body, "CAMPAIGN_SEND", campaignId);
            recipientRepository.markSent(recipientId, logId);
            campaignRepository.incrementSent(campaignId);
        } catch (Exception e) {
            log.warn("Campaign {} send to {} failed: {}", campaignId, email, e.getMessage());
            recipientRepository.markFailed(recipientId, e.getMessage());
            campaignRepository.incrementFailed(campaignId);
        }
    }

    /** Rewrites links through the click tracker and appends the open pixel + unsubscribe footer. */
    private String injectTracking(String html, String token, String unsubscribeUrl) {
        String clickBase = link("/api/track/click", token);
        Matcher m = HREF.matcher(html);
        StringBuilder rewritten = new StringBuilder();
        while (m.find()) {
            String url = m.group(1);
            String tracked = clickBase + "&u=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
            m.appendReplacement(rewritten, Matcher.quoteReplacement("href=\"" + tracked + "\""));
        }
        m.appendTail(rewritten);

        String pixel = "<img src=\"" + link("/api/track/open", token)
                + "\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\"/>";
        String footer = "<p style=\"font-size:11px;color:#888;margin-top:24px\">"
                + "If you no longer wish to receive these emails, "
                + "<a href=\"" + unsubscribeUrl + "\">unsubscribe</a>.</p>";
        return rewritten + footer + pixel;
    }

    private String link(String path, String token) {
        return properties.trackingBaseUrl() + path + "?t="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String resolveBody(Map<String, Object> campaign) {
        String templateId = (String) campaign.get("template_id");
        if (templateId != null && !templateId.isBlank()) {
            Optional<EmailService.EmailTemplate> tpl = emailService.getTemplate(templateId);
            if (tpl.isPresent()) {
                return tpl.get().bodyHtml();
            }
        }
        String body = (String) campaign.get("body_html");
        return body != null ? body : "";
    }

    private int resolveDailyQuota(String tenantId) {
        try {
            int q = quotaResolver.intQuota(tenantId, TenantTierQuotas.KEY_CAMPAIGN_EMAILS_PER_DAY);
            return q > 0 ? q : properties.dailySendLimit();
        } catch (Exception e) {
            return properties.dailySendLimit();
        }
    }

    private List<FilterCondition> parseFilters(Object filterJson) {
        if (filterJson == null) {
            return List.of();
        }
        try {
            String json = filterJson.toString();
            if (json.isBlank() || "null".equals(json)) {
                return List.of();
            }
            List<Map<String, Object>> raw = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            List<FilterCondition> filters = new ArrayList<>();
            for (Map<String, Object> f : raw) {
                String field = stringValue(f.get("field"));
                String op = stringValue(f.get("op"));
                Object value = f.get("value");
                if (field == null || op == null) {
                    continue;
                }
                FilterOperator operator = FilterOperator.valueOf(op.toUpperCase(Locale.ROOT));
                filters.add(new FilterCondition(field, operator, value));
            }
            return filters;
        } catch (Exception e) {
            log.warn("Ignoring unparseable campaign filter_json: {}", e.getMessage());
            return List.of();
        }
    }

    private static String stringValue(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void pace(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
