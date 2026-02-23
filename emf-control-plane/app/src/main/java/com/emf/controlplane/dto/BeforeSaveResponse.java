package com.emf.controlplane.dto;

import java.util.Map;

/**
 * Response from before-save workflow evaluation.
 * Contains field updates to apply to the record before persisting.
 */
public class BeforeSaveResponse {

    private Map<String, Object> fieldUpdates;
    private int rulesEvaluated;
    private int actionsExecuted;

    public BeforeSaveResponse() {}

    public BeforeSaveResponse(Map<String, Object> fieldUpdates, int rulesEvaluated, int actionsExecuted) {
        this.fieldUpdates = fieldUpdates;
        this.rulesEvaluated = rulesEvaluated;
        this.actionsExecuted = actionsExecuted;
    }

    public Map<String, Object> getFieldUpdates() { return fieldUpdates; }
    public void setFieldUpdates(Map<String, Object> fieldUpdates) { this.fieldUpdates = fieldUpdates; }
    public int getRulesEvaluated() { return rulesEvaluated; }
    public void setRulesEvaluated(int rulesEvaluated) { this.rulesEvaluated = rulesEvaluated; }
    public int getActionsExecuted() { return actionsExecuted; }
    public void setActionsExecuted(int actionsExecuted) { this.actionsExecuted = actionsExecuted; }
}
