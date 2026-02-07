package com.emf.controlplane.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SetOwdRequest {

    @NotBlank
    @Pattern(regexp = "PRIVATE|PUBLIC_READ|PUBLIC_READ_WRITE")
    private String internalAccess;

    @Pattern(regexp = "PRIVATE|PUBLIC_READ|PUBLIC_READ_WRITE")
    private String externalAccess = "PRIVATE";

    public SetOwdRequest() {}

    public SetOwdRequest(String internalAccess) {
        this.internalAccess = internalAccess;
    }

    public String getInternalAccess() { return internalAccess; }
    public void setInternalAccess(String internalAccess) { this.internalAccess = internalAccess; }

    public String getExternalAccess() { return externalAccess; }
    public void setExternalAccess(String externalAccess) { this.externalAccess = externalAccess; }
}
