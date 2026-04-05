package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ScimPatchOp {

    private List<String> schemas;
    @JsonProperty("Operations")
    private List<Operation> operations;

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public List<Operation> getOperations() { return operations; }
    public void setOperations(List<Operation> operations) { this.operations = operations; }

    public static class Operation {
        private String op;
        private String path;
        private Object value;

        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }
}
