package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kelta.worker.scim.ScimConstants;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimError {

    private List<String> schemas = List.of(ScimConstants.SCHEMA_ERROR);
    private String detail;
    private String status;
    private String scimType;

    public ScimError() {}

    public ScimError(String status, String detail) {
        this.status = status;
        this.detail = detail;
    }

    public ScimError(String status, String detail, String scimType) {
        this.status = status;
        this.detail = detail;
        this.scimType = scimType;
    }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScimType() { return scimType; }
    public void setScimType(String scimType) { this.scimType = scimType; }
}
