package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kelta.worker.scim.ScimConstants;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimGroup {

    private List<String> schemas = List.of(ScimConstants.SCHEMA_GROUP);
    private String id;
    private String externalId;
    private String displayName;
    private List<ScimMember> members;
    private ScimMeta meta;

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<ScimMember> getMembers() { return members; }
    public void setMembers(List<ScimMember> members) { this.members = members; }

    public ScimMeta getMeta() { return meta; }
    public void setMeta(ScimMeta meta) { this.meta = meta; }
}
