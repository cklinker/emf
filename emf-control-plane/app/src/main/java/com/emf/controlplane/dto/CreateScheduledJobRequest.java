package com.emf.controlplane.dto;

public class CreateScheduledJobRequest {

    private String name;
    private String description;
    private String jobType;
    private String jobReferenceId;
    private String cronExpression;
    private String timezone;
    private Boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getJobReferenceId() { return jobReferenceId; }
    public void setJobReferenceId(String jobReferenceId) { this.jobReferenceId = jobReferenceId; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
