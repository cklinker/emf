package com.emf.controlplane.dto;

import java.util.List;

public class CompositeResponse {

    private List<SubResponse> responses;

    public static class SubResponse {
        private String referenceId;
        private int httpStatusCode;
        private Object body;

        public SubResponse() {}

        public SubResponse(String referenceId, int httpStatusCode, Object body) {
            this.referenceId = referenceId;
            this.httpStatusCode = httpStatusCode;
            this.body = body;
        }

        public String getReferenceId() { return referenceId; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
        public int getHttpStatusCode() { return httpStatusCode; }
        public void setHttpStatusCode(int httpStatusCode) { this.httpStatusCode = httpStatusCode; }
        public Object getBody() { return body; }
        public void setBody(Object body) { this.body = body; }
    }

    public CompositeResponse() {}

    public CompositeResponse(List<SubResponse> responses) {
        this.responses = responses;
    }

    public List<SubResponse> getResponses() { return responses; }
    public void setResponses(List<SubResponse> responses) { this.responses = responses; }
}
