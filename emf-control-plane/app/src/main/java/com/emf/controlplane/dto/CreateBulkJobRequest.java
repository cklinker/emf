package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;

public class CreateBulkJobRequest {

    private String collectionId;
    private String operation;
    private String externalIdField;
    private Integer batchSize;
    private List<Map<String, Object>> records;

    public String getCollectionId() { return collectionId; }
    public void setCollectionId(String collectionId) { this.collectionId = collectionId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getExternalIdField() { return externalIdField; }
    public void setExternalIdField(String externalIdField) { this.externalIdField = externalIdField; }
    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }
    public List<Map<String, Object>> getRecords() { return records; }
    public void setRecords(List<Map<String, Object>> records) { this.records = records; }
}
