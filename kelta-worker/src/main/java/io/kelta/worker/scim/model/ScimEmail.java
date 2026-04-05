package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimEmail {

    private String value;
    private String type;
    private boolean primary;

    public ScimEmail() {}

    public ScimEmail(String value, String type, boolean primary) {
        this.value = value;
        this.type = type;
        this.primary = primary;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}
