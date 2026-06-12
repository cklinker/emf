package io.kelta.worker.listener;

import io.kelta.runtime.flow.FlowDefinitionValidator;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rejects flow create/update requests whose {@code definition} cannot be
 * parsed or whose state graph has dangling transitions, so authors see
 * structural errors at save time instead of at execution time.
 *
 * <p>The hook delegates to {@link FlowDefinitionValidator}: a parse-level
 * failure (missing {@code StartAt}, unknown state type, etc.) collapses
 * to a single error, and graph-level problems (dangling {@code Next},
 * Choice {@code Default}, {@code Catch.Next}, &amp;c.) are each reported
 * separately. All collected messages are returned as field-level errors
 * on {@code definition}, which the runtime translates into an HTTP 400.
 *
 * <p>On update, the hook is only run when the {@code definition} field is
 * part of the PATCH payload — partial updates that don't touch the
 * definition stay fast.
 *
 * @since 1.0.0
 */
public class FlowDefinitionValidationHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionValidationHook.class);
    private static final String FIELD = "definition";

    private final FlowDefinitionValidator validator;
    private final ObjectMapper objectMapper;

    public FlowDefinitionValidationHook(FlowDefinitionValidator validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override public String getCollectionName() { return "flows"; }

    @Override
    public int getOrder() {
        // Run before the event publisher (100) and schedule sync (150) so a
        // malformed definition is rejected before any side effects fire.
        return -100;
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        return validateIfPresent(record);
    }

    @Override
    public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                         Map<String, Object> previous, String tenantId) {
        if (!record.containsKey(FIELD) && !record.containsKey("flow_definition")) {
            return BeforeSaveResult.ok();
        }
        return validateIfPresent(record);
    }

    private BeforeSaveResult validateIfPresent(Map<String, Object> record) {
        String json = extractDefinitionJson(record);
        if (json == null) {
            return BeforeSaveResult.ok();
        }

        List<String> errors = validator.validate(json);
        if (errors.isEmpty()) {
            return BeforeSaveResult.ok();
        }

        List<BeforeSaveResult.ValidationError> validationErrors = new ArrayList<>(errors.size());
        for (String msg : errors) {
            validationErrors.add(new BeforeSaveResult.ValidationError(FIELD, msg));
        }
        return BeforeSaveResult.errors(validationErrors);
    }

    private String extractDefinitionJson(Map<String, Object> record) {
        Object raw = record.get(FIELD);
        if (raw == null) raw = record.get("flow_definition");
        if (raw == null) return null;
        if (raw instanceof String s) return s.isBlank() ? null : s;
        try {
            return objectMapper.writeValueAsString(raw);
        } catch (Exception e) {
            log.warn("Failed to serialize flow definition for validation: {}", e.getMessage());
            return null;
        }
    }
}
