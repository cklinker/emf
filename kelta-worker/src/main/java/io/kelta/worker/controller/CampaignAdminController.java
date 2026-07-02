package io.kelta.worker.controller;

import io.kelta.jsonapi.JsonApiResponseBuilder;
import io.kelta.runtime.context.TenantContext;
import io.kelta.runtime.module.integration.spi.EmailService;
import io.kelta.worker.config.CampaignProperties;
import io.kelta.worker.repository.BootstrapRepository;
import io.kelta.worker.repository.CampaignRecipientRepository;
import io.kelta.worker.repository.CampaignRepository;
import io.kelta.worker.repository.EmailSuppressionRepository;
import io.kelta.worker.service.CerbosPermissionResolver;
import io.kelta.worker.service.TenantQuotaResolver;
import io.kelta.worker.service.TenantTierQuotas;
import io.kelta.worker.service.campaign.MergeFieldRenderer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin API for mass-email campaigns. All endpoints require {@code MANAGE_CAMPAIGNS}.
 *
 * <p>The "campaigns" system collection is read-only over the generic API, so every mutation and
 * the schedule/send/cancel lifecycle actions flow through here where the permission and the daily
 * send governor limit are enforced. This is a spam-capable surface, so it is deliberately not
 * reachable through the generic collection write path.
 *
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/campaigns")
public class CampaignAdminController {

    private static final Logger log = LoggerFactory.getLogger(CampaignAdminController.class);
    private static final String PERMISSION = "MANAGE_CAMPAIGNS";
    private static final int MAX_PAGE = 200;

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final EmailSuppressionRepository suppressionRepository;
    private final CerbosPermissionResolver permissionResolver;
    private final BootstrapRepository bootstrapRepository;
    private final TenantQuotaResolver quotaResolver;
    private final EmailService emailService;
    private final CampaignProperties properties;
    private final ObjectMapper objectMapper;

    public CampaignAdminController(CampaignRepository campaignRepository,
                                   CampaignRecipientRepository recipientRepository,
                                   EmailSuppressionRepository suppressionRepository,
                                   CerbosPermissionResolver permissionResolver,
                                   BootstrapRepository bootstrapRepository,
                                   TenantQuotaResolver quotaResolver,
                                   EmailService emailService,
                                   CampaignProperties properties,
                                   ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.suppressionRepository = suppressionRepository;
        this.permissionResolver = permissionResolver;
        this.bootstrapRepository = bootstrapRepository;
        this.quotaResolver = quotaResolver;
        this.emailService = emailService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- CRUD

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest request,
                                                    @RequestParam(defaultValue = "50") int limit,
                                                    @RequestParam(defaultValue = "0") int offset) {
        requirePermission(request);
        String tenantId = requireTenant();
        List<Map<String, Object>> rows = campaignRepository.list(tenantId, clamp(limit), Math.max(0, offset));
        List<Map<String, Object>> records = rows.stream().map(this::project).toList();
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("campaigns", records));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        Map<String, Object> row = load(id);
        return ResponseEntity.ok(JsonApiResponseBuilder.single("campaigns", id, project(row)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(HttpServletRequest request,
                                                      @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        Map<String, Object> attrs = attributes(body);
        validateRequired(attrs);
        String id = campaignRepository.create(tenantId, normalize(attrs), actor(request));
        Map<String, Object> row = campaignRepository.findById(id, tenantId).orElseThrow();
        log.info("Campaign {} created in tenant {}", id, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JsonApiResponseBuilder.single("campaigns", id, project(row)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(HttpServletRequest request, @PathVariable String id,
                                                      @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        Map<String, Object> existing = load(id);
        Map<String, Object> merged = mergeForUpdate(existing, attributes(body));
        int updated = campaignRepository.update(id, tenantId, merged, actor(request));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT or SCHEDULED campaigns can be edited");
        }
        Map<String, Object> row = campaignRepository.findById(id, tenantId).orElseThrow();
        return ResponseEntity.ok(JsonApiResponseBuilder.single("campaigns", id, project(row)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        String tenantId = requireTenant();
        int removed = campaignRepository.deleteDraft(id, tenantId);
        if (removed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A running or sent campaign cannot be deleted");
        }
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------- Actions

    @PostMapping("/{id}/send")
    public ResponseEntity<Map<String, Object>> send(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        String tenantId = requireTenant();
        load(id);
        enforceDailyGovernor(tenantId);
        int n = campaignRepository.enqueue(id, tenantId, "QUEUED", null);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT or SCHEDULED campaigns can be sent");
        }
        return ResponseEntity.accepted().body(Map.of("status", "QUEUED", "id", id));
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<Map<String, Object>> schedule(HttpServletRequest request, @PathVariable String id,
                                                        @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        load(id);
        Object scheduledAt = body.get("scheduledAt");
        if (scheduledAt == null || scheduledAt.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt is required");
        }
        int n = campaignRepository.enqueue(id, tenantId, "SCHEDULED", scheduledAt.toString());
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT or SCHEDULED campaigns can be scheduled");
        }
        return ResponseEntity.accepted().body(Map.of("status", "SCHEDULED", "id", id, "scheduledAt", scheduledAt));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        String tenantId = requireTenant();
        load(id);
        int n = campaignRepository.cancel(id, tenantId);
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT, SCHEDULED, or QUEUED campaigns can be cancelled");
        }
        return ResponseEntity.ok(Map.of("status", "CANCELLED", "id", id));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> stats(HttpServletRequest request, @PathVariable String id) {
        requirePermission(request);
        Map<String, Object> row = load(id);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("status", row.get("status"));
        stats.put("totalRecipients", num(row.get("total_recipients")));
        stats.put("sent", num(row.get("sent_count")));
        stats.put("failed", num(row.get("failed_count")));
        stats.put("opens", num(row.get("open_count")));
        stats.put("clicks", num(row.get("click_count")));
        stats.put("unsubscribes", num(row.get("unsubscribe_count")));
        stats.put("startedAt", row.get("started_at"));
        stats.put("completedAt", row.get("completed_at"));
        return ResponseEntity.ok(Map.of("data", stats));
    }

    @GetMapping("/{id}/recipients")
    public ResponseEntity<Map<String, Object>> recipients(HttpServletRequest request, @PathVariable String id,
                                                          @RequestParam(defaultValue = "100") int limit,
                                                          @RequestParam(defaultValue = "0") int offset) {
        requirePermission(request);
        String tenantId = requireTenant();
        load(id);
        List<Map<String, Object>> rows =
                recipientRepository.listByCampaign(id, tenantId, clamp(limit), Math.max(0, offset));
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("campaign-recipients", rows));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> test(HttpServletRequest request, @PathVariable String id,
                                                    @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        Map<String, Object> row = load(id);
        String to = str(body.get("email"));
        if (to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        Map<String, Object> vars = Map.of("email", to);
        String subject = MergeFieldRenderer.render((String) row.get("subject"), vars);
        String bodyHtml = MergeFieldRenderer.render(resolveBody(row), vars);
        emailService.queueEmail(tenantId, to, "[TEST] " + subject, bodyHtml, "CAMPAIGN_TEST", id);
        return ResponseEntity.ok(Map.of("status", "sent", "to", to));
    }

    // --------------------------------------------------------- Suppressions

    @GetMapping("/suppressions")
    public ResponseEntity<Map<String, Object>> listSuppressions(HttpServletRequest request,
                                                                @RequestParam(defaultValue = "100") int limit,
                                                                @RequestParam(defaultValue = "0") int offset) {
        requirePermission(request);
        String tenantId = requireTenant();
        List<Map<String, Object>> rows = suppressionRepository.list(tenantId, clamp(limit), Math.max(0, offset));
        return ResponseEntity.ok(JsonApiResponseBuilder.collection("email-suppressions", rows));
    }

    @PostMapping("/suppressions")
    public ResponseEntity<Map<String, Object>> addSuppression(HttpServletRequest request,
                                                              @RequestBody Map<String, Object> body) {
        requirePermission(request);
        String tenantId = requireTenant();
        String email = str(attributes(body).get("email"));
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        String reason = Optional.ofNullable(str(attributes(body).get("reason"))).orElse("MANUAL");
        boolean added = suppressionRepository.add(tenantId, email, reason, null, actor(request));
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK)
                .body(Map.of("status", added ? "added" : "exists", "email", email));
    }

    @DeleteMapping("/suppressions")
    public ResponseEntity<Map<String, Object>> removeSuppression(HttpServletRequest request,
                                                                @RequestParam String email) {
        requirePermission(request);
        String tenantId = requireTenant();
        int removed = suppressionRepository.remove(tenantId, email);
        return ResponseEntity.ok(Map.of("status", removed > 0 ? "removed" : "absent", "email", email));
    }

    // ------------------------------------------------------------- Helpers

    private void requirePermission(HttpServletRequest request) {
        String profileId = permissionResolver.getProfileId(request);
        if (profileId == null || profileId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No identity");
        }
        boolean granted = bootstrapRepository.findProfileSystemPermissions(profileId).stream()
                .anyMatch(p -> PERMISSION.equals(p.get("permission_name"))
                        && Boolean.TRUE.equals(p.get("granted")));
        if (!granted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, PERMISSION + " permission required");
        }
    }

    private void enforceDailyGovernor(String tenantId) {
        int quota = quotaResolver.intQuota(tenantId, TenantTierQuotas.KEY_CAMPAIGN_EMAILS_PER_DAY);
        int sentToday = recipientRepository.countSentToday(tenantId);
        if (sentToday >= quota) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily campaign send limit reached (" + quota + ")");
        }
    }

    private String requireTenant() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No tenant context");
        }
        return tenantId;
    }

    private Map<String, Object> load(String id) {
        return campaignRepository.findById(id, requireTenant())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private String actor(HttpServletRequest request) {
        return permissionResolver.getProfileId(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attributes(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        Object data = body.get("data");
        if (data instanceof Map<?, ?> dataMap && dataMap.get("attributes") instanceof Map<?, ?> attrs) {
            return (Map<String, Object>) attrs;
        }
        return body;
    }

    private void validateRequired(Map<String, Object> a) {
        for (String key : List.of("name", "subject", "targetCollection", "recipientEmailField")) {
            if (str(a.get(key)) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
            }
        }
    }

    /** Serializes complex attributes (filterJson) to a JSON string for the {@code ::jsonb} bind. */
    private Map<String, Object> normalize(Map<String, Object> a) {
        Map<String, Object> out = new LinkedHashMap<>(a);
        Object filter = a.get("filterJson");
        if (filter != null && !(filter instanceof String)) {
            out.put("filterJson", objectMapper.writeValueAsString(filter));
        }
        return out;
    }

    /** Builds the full editable attribute set for an update by overlaying the request onto the row. */
    private Map<String, Object> mergeForUpdate(Map<String, Object> row, Map<String, Object> patch) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", pick(patch, row, "name", "name"));
        a.put("description", pick(patch, row, "description", "description"));
        a.put("subject", pick(patch, row, "subject", "subject"));
        a.put("bodyHtml", pick(patch, row, "bodyHtml", "body_html"));
        a.put("templateId", pick(patch, row, "templateId", "template_id"));
        a.put("targetCollection", pick(patch, row, "targetCollection", "target_collection"));
        a.put("recipientEmailField", pick(patch, row, "recipientEmailField", "recipient_email_field"));
        a.put("listViewId", pick(patch, row, "listViewId", "list_view_id"));
        a.put("fromName", pick(patch, row, "fromName", "from_name"));
        a.put("fromAddress", pick(patch, row, "fromAddress", "from_address"));
        a.put("scheduledAt", pick(patch, row, "scheduledAt", "scheduled_at"));
        Object filter = patch.containsKey("filterJson") ? patch.get("filterJson") : row.get("filter_json");
        a.put("filterJson", filter);
        return normalize(a);
    }

    private Object pick(Map<String, Object> patch, Map<String, Object> row, String patchKey, String rowKey) {
        return patch.containsKey(patchKey) ? patch.get(patchKey) : row.get(rowKey);
    }

    private String resolveBody(Map<String, Object> row) {
        String templateId = str(row.get("template_id"));
        if (templateId != null) {
            Optional<EmailService.EmailTemplate> tpl = emailService.getTemplate(templateId);
            if (tpl.isPresent()) {
                return tpl.get().bodyHtml();
            }
        }
        String body = (String) row.get("body_html");
        return body != null ? body : "";
    }

    /** Projects a snake_case DB row into the camelCase JSON:API attribute set. */
    private Map<String, Object> project(Map<String, Object> row) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", row.get("name"));
        a.put("description", row.get("description"));
        a.put("subject", row.get("subject"));
        a.put("bodyHtml", row.get("body_html"));
        a.put("templateId", row.get("template_id"));
        a.put("targetCollection", row.get("target_collection"));
        a.put("recipientEmailField", row.get("recipient_email_field"));
        a.put("filterJson", row.get("filter_json"));
        a.put("listViewId", row.get("list_view_id"));
        a.put("fromName", row.get("from_name"));
        a.put("fromAddress", row.get("from_address"));
        a.put("status", row.get("status"));
        a.put("scheduledAt", row.get("scheduled_at"));
        a.put("totalRecipients", num(row.get("total_recipients")));
        a.put("sentCount", num(row.get("sent_count")));
        a.put("failedCount", num(row.get("failed_count")));
        a.put("openCount", num(row.get("open_count")));
        a.put("clickCount", num(row.get("click_count")));
        a.put("unsubscribeCount", num(row.get("unsubscribe_count")));
        a.put("startedAt", row.get("started_at"));
        a.put("completedAt", row.get("completed_at"));
        a.put("errorMessage", row.get("error_message"));
        a.put("createdAt", row.get("created_at"));
        return a;
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(MAX_PAGE, limit));
    }

    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
