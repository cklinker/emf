package io.kelta.auth.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class KeltaUserDetailsMixin {

    @JsonCreator
    KeltaUserDetailsMixin(
            @JsonProperty("id") String id,
            @JsonProperty("email") String email,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("profileId") String profileId,
            @JsonProperty("profileName") String profileName,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("passwordHash") String passwordHash,
            @JsonProperty("active") boolean active,
            @JsonProperty("locked") boolean locked,
            @JsonProperty("forceChangePassword") boolean forceChangePassword) {
    }
}
