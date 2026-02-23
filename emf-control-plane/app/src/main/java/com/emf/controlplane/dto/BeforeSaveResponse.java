package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;

/**
 * Response from before-save workflow evaluation.
 * Contains field updates to apply to the record before persisting,
 * and optionally validation errors that block the save.
 */
public class BeforeSaveResponse {

    private Map<String, Object> fieldUpdates;
    private int rulesEvaluated;
    private int actionsExecuted;
    private List<Map<String, String>> errors;

    public BeforeSaveResponse() {}

    public BeforeSaveResponse(Map<String, Object> fieldUpdates, int rulesEvaluated, int actionsExecuted) {
        this.fieldUpdates = fieldUpdates;
        this.rulesEvaluated = rulesEvaluated;
        this.actionsExecuted = actionsExecuted;
    }

    public BeforeSaveResponse(Map<String, Object> fieldUpdates, int rulesEvaluated, int actionsExecuted,
                               List<Map<String, String>> errors) {
        this.fieldUpdates = fieldUpdates;
        this.rulesEvaluated = rulesEvaluated;
        this.actionsExecuted = actionsExecuted;
        this.errors = errors;
    }

    public Map<String, Object> getFieldUpdates() { return fieldUpdates; }
    public void setFieldUpdates(Map<String, Object> fieldUpdates) { this.fieldUpdates = fieldUpdates; }
    public int getRulesEvaluated() { return rulesEvaluated; }
    public void setRulesEvaluated(int rulesEvaluated) { this.rulesEvaluated = rulesEvaluated; }
    public int getActionsExecuted() { return actionsExecuted; }
    public void setActionsExecuted(int actionsExecuted) { this.actionsExecuted = actionsExecuted; }
    public List<Map<String, String>> getErrors() { return errors; }
    public void setErrors(List<Map<String, String>> errors) { this.errors = errors; }

    /**
     * Returns true if this response contains validation errors that should block the save.
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }
}
