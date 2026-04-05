package io.kelta.worker.scim;

public final class ScimConstants {

    private ScimConstants() {}

    public static final String SCHEMA_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String SCHEMA_GROUP = "urn:ietf:params:scim:schemas:core:2.0:Group";
    public static final String SCHEMA_LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public static final String SCHEMA_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";
    public static final String SCHEMA_PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
    public static final String SCHEMA_SP_CONFIG = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";
    public static final String SCHEMA_RESOURCE_TYPE = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";
    public static final String SCHEMA_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Schema";

    public static final String CONTENT_TYPE_SCIM = "application/scim+json";

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;
}
