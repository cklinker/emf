package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimMember {

    private String value;
    @JsonProperty("$ref")
    private String ref;
    private String display;
    private String type;

    public ScimMember() {}

    public ScimMember(String value, String ref, String display, String type) {
        this.value = value;
        this.ref = ref;
        this.display = display;
        this.type = type;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
