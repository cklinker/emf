package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimName {

    private String formatted;
    private String familyName;
    private String givenName;

    public ScimName() {}

    public ScimName(String givenName, String familyName) {
        this.givenName = givenName;
        this.familyName = familyName;
        this.formatted = (givenName != null ? givenName : "") +
                (givenName != null && familyName != null ? " " : "") +
                (familyName != null ? familyName : "");
        if (this.formatted.isBlank()) {
            this.formatted = null;
        }
    }

    public String getFormatted() { return formatted; }
    public void setFormatted(String formatted) { this.formatted = formatted; }

    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }

    public String getGivenName() { return givenName; }
    public void setGivenName(String givenName) { this.givenName = givenName; }
}
