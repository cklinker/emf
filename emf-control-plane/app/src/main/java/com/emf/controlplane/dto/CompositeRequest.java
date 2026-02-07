package com.emf.controlplane.dto;

import java.util.List;
import java.util.Map;

public class CompositeRequest {

    private List<SubRequest> requests;

    public static class SubRequest {
        private String method;
        private String url;
        private Map<String, Object> body;
        private String referenceId;

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Map<String, Object> getBody() { return body; }
        public void setBody(Map<String, Object> body) { this.body = body; }
        public String getReferenceId() { return referenceId; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    }

    public List<SubRequest> getRequests() { return requests; }
    public void setRequests(List<SubRequest> requests) { this.requests = requests; }
}
