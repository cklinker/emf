package com.emf.controlplane.action.handlers;

import com.emf.controlplane.action.CollectionActionHandler;
import com.emf.controlplane.service.BulkJobService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Action handler for aborting a bulk job.
 */
@Component
public class AbortBulkJobHandler implements CollectionActionHandler {

    private final BulkJobService bulkJobService;

    public AbortBulkJobHandler(BulkJobService bulkJobService) {
        this.bulkJobService = bulkJobService;
    }

    @Override
    public String getCollectionName() {
        return "bulk-jobs";
    }

    @Override
    public String getActionName() {
        return "abort";
    }

    @Override
    public boolean isInstanceAction() {
        return true;
    }

    @Override
    public Object execute(String id, Map<String, Object> body, String tenantId, String userId) {
        bulkJobService.abortJob(id);
        return Map.of("status", "aborted", "id", id);
    }
}
