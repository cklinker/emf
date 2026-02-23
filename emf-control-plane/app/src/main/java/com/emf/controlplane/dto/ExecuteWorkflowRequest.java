package com.emf.controlplane.dto;

import java.util.List;

/**
 * Request body for manually executing a workflow rule against specific records.
 */
public class ExecuteWorkflowRequest {

    private List<String> recordIds;

    public ExecuteWorkflowRequest() {}

    public ExecuteWorkflowRequest(List<String> recordIds) {
        this.recordIds = recordIds;
    }

    public List<String> getRecordIds() { return recordIds; }
    public void setRecordIds(List<String> recordIds) { this.recordIds = recordIds; }
}
