package com.emf.controlplane.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "bulk_job_result")
public class BulkJobResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_job_id", nullable = false)
    private BulkJob bulkJob;

    @Column(name = "record_index")
    private int recordIndex;

    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public BulkJobResult() { super(); }

    public BulkJob getBulkJob() { return bulkJob; }
    public void setBulkJob(BulkJob bulkJob) { this.bulkJob = bulkJob; }
    public int getRecordIndex() { return recordIndex; }
    public void setRecordIndex(int recordIndex) { this.recordIndex = recordIndex; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
