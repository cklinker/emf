package com.emf.controlplane.dto;

import java.util.Map;

/**
 * DTO for workflow execution analytics.
 */
public class WorkflowAnalyticsDto {

    private long totalExecutions;
    private long successCount;
    private long failureCount;
    private long partialFailureCount;
    private double successRate;
    private Double avgDurationMs;
    private Map<String, Long> executionsByStatus;
    private Map<String, Long> executionsByTriggerType;

    public long getTotalExecutions() { return totalExecutions; }
    public void setTotalExecutions(long totalExecutions) { this.totalExecutions = totalExecutions; }
    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }
    public long getFailureCount() { return failureCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public long getPartialFailureCount() { return partialFailureCount; }
    public void setPartialFailureCount(long partialFailureCount) { this.partialFailureCount = partialFailureCount; }
    public double getSuccessRate() { return successRate; }
    public void setSuccessRate(double successRate) { this.successRate = successRate; }
    public Double getAvgDurationMs() { return avgDurationMs; }
    public void setAvgDurationMs(Double avgDurationMs) { this.avgDurationMs = avgDurationMs; }
    public Map<String, Long> getExecutionsByStatus() { return executionsByStatus; }
    public void setExecutionsByStatus(Map<String, Long> executionsByStatus) { this.executionsByStatus = executionsByStatus; }
    public Map<String, Long> getExecutionsByTriggerType() { return executionsByTriggerType; }
    public void setExecutionsByTriggerType(Map<String, Long> executionsByTriggerType) { this.executionsByTriggerType = executionsByTriggerType; }
}
