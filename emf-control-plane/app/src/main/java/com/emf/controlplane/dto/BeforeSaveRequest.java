package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;

/**
 * Request body for before-save workflow evaluation.
 * Called synchronously by the worker during record create/update operations.
 */
public class BeforeSaveRequest {

    private String tenantId;
    private String collectionId;
    private String collectionName;
    private String recordId;
    private Map<String, Object> data;
    private Map<String, Object> previousData;
    private List<String> changedFields;
    private String userId;
    private String changeType; // "CREATE" or "UPDATE"

    public BeforeSaveRequest() {}

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public Map<String, Object> getPreviousData() { return previousData; }
    public void setPreviousData(Map<String, Object> previousData) { this.previousData = previousData; }
    public List<String> getChangedFields() { return changedFields; }
    public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
}
