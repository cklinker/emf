package io.kelta.worker.scim.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kelta.worker.scim.ScimConstants;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimListResponse<T> {

    private List<String> schemas = List.of(ScimConstants.SCHEMA_LIST_RESPONSE);
    private int totalResults;
    private int startIndex;
    private int itemsPerPage;
    @JsonProperty("Resources")
    private List<T> resources;

    public ScimListResponse() {}

    public ScimListResponse(List<T> resources, int totalResults, int startIndex, int itemsPerPage) {
        this.resources = resources;
        this.totalResults = totalResults;
        this.startIndex = startIndex;
        this.itemsPerPage = itemsPerPage;
    }

    public List<String> getSchemas() { return schemas; }
    public void setSchemas(List<String> schemas) { this.schemas = schemas; }

    public int getTotalResults() { return totalResults; }
    public void setTotalResults(int totalResults) { this.totalResults = totalResults; }

    public int getStartIndex() { return startIndex; }
    public void setStartIndex(int startIndex) { this.startIndex = startIndex; }

    public int getItemsPerPage() { return itemsPerPage; }
    public void setItemsPerPage(int itemsPerPage) { this.itemsPerPage = itemsPerPage; }

    public List<T> getResources() { return resources; }
    public void setResources(List<T> resources) { this.resources = resources; }
}
