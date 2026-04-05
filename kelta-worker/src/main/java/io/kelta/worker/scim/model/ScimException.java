package io.kelta.worker.scim.model;

import org.springframework.http.HttpStatus;

public class ScimException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String scimType;

    public ScimException(HttpStatus httpStatus, String detail) {
        super(detail);
        this.httpStatus = httpStatus;
        this.scimType = null;
    }

    public ScimException(HttpStatus httpStatus, String detail, String scimType) {
        super(detail);
        this.httpStatus = httpStatus;
        this.scimType = scimType;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getScimType() { return scimType; }

    public ScimError toScimError() {
        return new ScimError(String.valueOf(httpStatus.value()), getMessage(), scimType);
    }
}
