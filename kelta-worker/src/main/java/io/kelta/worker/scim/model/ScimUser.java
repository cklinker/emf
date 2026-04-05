package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kelta.worker.scim.ScimConstants;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUser {

    private List<String> schemas = List.of(ScimConstants.SCHEMA_USER);
    private String id;
    private String externalId;
    private String userName;
    private ScimName name;
    private String displayName;
    private String locale;
    private String timezone;
    private boolean active = true;
    private List<ScimEmail> emails;
    private ScimMeta meta;

    @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    private ScimEnterpriseUser enterpriseUser;

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public ScimName getName() { return name; }
    public void setName(ScimName name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<ScimEmail> getEmails() { return emails; }
    public void setEmails(List<ScimEmail> emails) { this.emails = emails; }

    public ScimMeta getMeta() { return meta; }
    public void setMeta(ScimMeta meta) { this.meta = meta; }

    public ScimEnterpriseUser getEnterpriseUser() { return enterpriseUser; }
    public void setEnterpriseUser(ScimEnterpriseUser enterpriseUser) { this.enterpriseUser = enterpriseUser; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScimEnterpriseUser {
        private String employeeNumber;
        private ScimManager manager;

        public String getEmployeeNumber() { return employeeNumber; }
        public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }

        public ScimManager getManager() { return manager; }
        public void setManager(ScimManager manager) { this.manager = manager; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScimManager {
        private String value;
        private String displayName;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
