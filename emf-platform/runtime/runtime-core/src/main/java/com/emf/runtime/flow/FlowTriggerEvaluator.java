package com.emf.runtime.flow;

import com.emf.runtime.event.ChangeType;
import com.emf.runtime.event.RecordChangeEvent;
import com.emf.runtime.formula.FormulaEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Evaluates whether a trigger event matches a flow's trigger configuration.
 * <p>
 * Used by {@code FlowEventListener} to determine which flows should be
 * started in response to a record change event.
 *
 * @since 1.0.0
 */
public class FlowTriggerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(FlowTriggerEvaluator.class);

    private final FormulaEvaluator formulaEvaluator;

    public FlowTriggerEvaluator(FormulaEvaluator formulaEvaluator) {
        this.formulaEvaluator = formulaEvaluator;
    }

    /**
     * Evaluates whether a record change event matches a RECORD_TRIGGERED flow's trigger config.
     *
     * @param event         the record change event
     * @param triggerConfig the flow's trigger_config JSONB as a map
     * @return true if the event matches the trigger conditions
     */
    @SuppressWarnings("unchecked")
    public boolean matchesRecordTrigger(RecordChangeEvent event, Map<String, Object> triggerConfig) {
        if (triggerConfig == null) {
            return false;
        }

        // Check collection
        String collection = (String) triggerConfig.get("collection");
        if (collection != null && !collection.equals(event.getCollectionName())) {
            return false;
        }

        // Check events (CREATED, UPDATED, DELETED)
        List<String> events = (List<String>) triggerConfig.get("events");
        if (events != null && !events.isEmpty()) {
            String changeType = event.getChangeType().name();
            if (!events.contains(changeType)) {
                return false;
            }
        }

        // Check trigger fields (for UPDATE events only)
        if (event.getChangeType() == ChangeType.UPDATED) {
            List<String> triggerFields = (List<String>) triggerConfig.get("triggerFields");
            if (triggerFields != null && !triggerFields.isEmpty()) {
                List<String> changedFields = event.getChangedFields();
                if (changedFields == null || changedFields.stream().noneMatch(triggerFields::contains)) {
                    return false;
                }
            }
        }

        // Check filter formula
        String filterFormula = (String) triggerConfig.get("filterFormula");
        if (filterFormula != null && !filterFormula.isBlank()) {
            try {
                boolean matches = formulaEvaluator.evaluateBoolean(filterFormula, event.getData());
                if (!matches) {
                    return false;
                }
            } catch (Exception e) {
                log.warn("Filter formula evaluation failed for flow trigger: {}", e.getMessage());
                return false;
            }
        }

        return true;
    }
}
