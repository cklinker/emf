package io.kelta.worker.listener;

import io.kelta.runtime.flow.FlowDefinitionValidator;
import io.kelta.runtime.workflow.BeforeSaveHook;
import io.kelta.runtime.workflow.BeforeSaveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rejects flow create/update calls whose {@code definition} JSON would crash
 * the flow engine at execution time, returning a 400 that lists every problem
 * found so the author can fix them in one round-trip.
 *
 * <p>Without this hook a malformed definition (e.g. missing top-level
 * {@code StartAt}, a {@code Next}/{@code Default} pointing at a state that
 * isn't in {@code States}, or a Map state without an {@code Iterator}) is
 * accepted at save time and only blows up later as a
 * {@link io.kelta.runtime.flow.FlowDefinitionException} the first time the
 * flow is triggered — at which point the author is no longer at their desk.
 *
 * <p>Runs at order {@code 50} so structural problems are caught before the
 * config event publisher (100) broadcasts an invalid flow to other pods and
 * before the schedule sync hook (150) tries to set up a cron job for it.
 *
 * @since 1.0.0
 */
public class FlowDefinitionValidationHook implements BeforeSaveHook {

    private static final Logger log = LoggerFactory.getLogger(FlowDefinitionValidationHook.class);

    private final FlowDefinitionValidator validator;
    private final ObjectMapper objectMapper;

    public FlowDefinitionValidationHook(ObjectMapper objectMapper) {
        this.validator = new FlowDefinitionValidator(objectMapper);
        this.objectMapper = objectMapper;
    }

    @Override
    public String getCollectionName() {
        return "flows";
    }

    @Override
    public int getOrder() {
        // Run before FlowConfigEventPublisher (100) and FlowScheduleSyncHook (150)
        // so a bad definition never reaches the NATS broadcast or scheduler.
        return 50;
    }

    @Override
    public BeforeSaveResult beforeCreate(Map<String, Object> record, String tenantId) {
        return validateDefinition(record, /* requireDefinition= */ true);
    }

    @Override
    public BeforeSaveResult beforeUpdate(String id, Map<String, Object> record,
                                          Map<String, Object> previous, String tenantId) {
        // PUTs may be partial — e.g. flipping `active` — so a missing
        // definition field is fine on update; we only validate what was sent.
        return validateDefinition(record, /* requireDefinition= */ false);
    }

    private BeforeSaveResult validateDefinition(Map<String, Object> record, boolean requireDefinition) {
        if (!record.containsKey("definition")) {
            if (requireDefinition) {
                return BeforeSaveResult.error("definition", "Flow definition is required");
            }
            return BeforeSaveResult.ok();
        }
        String json = serialize(record.get("definition"));
        if (json == null) {
            return BeforeSaveResult.error("definition",
                    "Flow definition could not be read as JSON");
        }
        FlowDefinitionValidator.Result result = validator.validate(json);
        if (result.isValid()) {
            return BeforeSaveResult.ok();
        }
        List<BeforeSaveResult.ValidationError> errors = result.errors().stream()
                .map(msg -> new BeforeSaveResult.ValidationError("definition", msg))
                .collect(Collectors.toList());
        return BeforeSaveResult.errors(errors);
    }

    /**
     * The {@code definition} column is jsonb, so depending on the call path
     * (UI POST vs. internal seeding vs. JSON:API write) we may see it as a
     * {@code Map}, a {@code List}, or an already-stringified JSON value.
     * Mirrors {@link FlowScheduleSyncHook#extractTriggerConfig} so all flow
     * hooks treat the payload the same way.
     */
    private String serialize(Object definition) {
        if (definition == null) {
            return null;
        }
        if (definition instanceof String s) {
            return s.isBlank() ? null : s;
        }
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (Exception e) {
            log.warn("Failed to serialize flow definition for validation: {}", e.getMessage());
            return null;
        }
    }
}
