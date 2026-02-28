package com.emf.runtime.event;

/**
 * Payload for module lifecycle change events published to Kafka.
 * Propagates module install/enable/disable/uninstall events to all worker pods.
 *
 * @since 1.0.0
 */
public class ModuleChangedPayload {

    private String id;
    private String tenantId;
    private String moduleId;
    private String name;
    private String version;
    private String s3Key;
    private String moduleClass;
    private String manifest;
    private ModuleChangeType changeType;

    public ModuleChangedPayload() {
    }

    public ModuleChangedPayload(String id, String tenantId, String moduleId, String name,
                                 String version, String s3Key, String moduleClass,
                                 String manifest, ModuleChangeType changeType) {
        this.id = id;
        this.tenantId = tenantId;
        this.moduleId = moduleId;
        this.name = name;
        this.version = version;
        this.s3Key = s3Key;
        this.moduleClass = moduleClass;
        this.manifest = manifest;
        this.changeType = changeType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getModuleId() { return moduleId; }
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }

    public String getModuleClass() { return moduleClass; }
    public void setModuleClass(String moduleClass) { this.moduleClass = moduleClass; }

    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }

    public ModuleChangeType getChangeType() { return changeType; }
    public void setChangeType(ModuleChangeType changeType) { this.changeType = changeType; }

    @Override
    public String toString() {
        return "ModuleChangedPayload{" +
            "tenantId='" + tenantId + '\'' +
            ", moduleId='" + moduleId + '\'' +
            ", name='" + name + '\'' +
            ", version='" + version + '\'' +
            ", changeType=" + changeType +
            '}';
    }
}
