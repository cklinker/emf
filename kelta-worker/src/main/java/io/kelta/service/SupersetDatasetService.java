package io.kelta.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages Superset datasets (one per collection per tenant).
 *
 * <p>Datasets are created as virtual SQL datasets that query from the
 * tenant's schema. Field-level permissions are applied by excluding
 * HIDDEN fields from the SQL column list.
 *
 * @since 1.0.0
 */
public class SupersetDatasetService {

    private static final Logger log = LoggerFactory.getLogger(SupersetDatasetService.class);

    private final SupersetApiClient apiClient;
    private final JdbcTemplate jdbcTemplate;

    public SupersetDatasetService(SupersetApiClient apiClient, JdbcTemplate jdbcTemplate) {
        this.apiClient = apiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Syncs datasets for all collections in a tenant.
     *
     * @param tenantId   the tenant UUID
     * @param tenantSlug the tenant slug
     */
    public void syncDatasets(String tenantId, String tenantSlug) {
        try {
            int dbId = apiClient.findDatabaseId(tenantSlug);
            if (dbId <= 0) {
                log.warn("No Superset database connection found for tenant '{}' — skipping dataset sync",
                        tenantSlug);
                return;
            }

            // Get existing datasets
            var existingDatasets = apiClient.listDatasets(dbId);
            var existingNames = existingDatasets.stream()
                    .map(d -> (String) d.get("table_name"))
                    .collect(Collectors.toSet());

            // Get all non-system collections for this tenant
            List<Map<String, Object>> collections = jdbcTemplate.queryForList(
                    "SELECT c.id, c.name FROM collection c "
                    + "WHERE c.tenant_id = ? AND c.system_collection = false "
                    + "ORDER BY c.name",
                    tenantId
            );

            for (Map<String, Object> collection : collections) {
                String collectionId = (String) collection.get("id");
                String collectionName = (String) collection.get("name");
                String datasetName = "kelta_" + collectionName;

                if (existingNames.contains(datasetName)) {
                    log.debug("Dataset '{}' already exists for tenant '{}'", datasetName, tenantSlug);
                    continue;
                }

                String sql = buildDatasetSql(tenantId, tenantSlug, collectionId, collectionName);
                int datasetId = apiClient.createDataset(dbId, datasetName, sql);
                if (datasetId > 0) {
                    log.info("Created Superset dataset '{}' for tenant '{}' (datasetId={})",
                            datasetName, tenantSlug, datasetId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync Superset datasets for tenant '{}': {}", tenantSlug, e.getMessage());
        }
    }

    /**
     * Creates or updates a dataset for a specific collection.
     *
     * @param tenantId       the tenant UUID
     * @param tenantSlug     the tenant slug
     * @param collectionId   the collection UUID
     * @param collectionName the collection name
     */
    public void syncDatasetForCollection(String tenantId, String tenantSlug,
                                          String collectionId, String collectionName) {
        try {
            int dbId = apiClient.findDatabaseId(tenantSlug);
            if (dbId <= 0) {
                log.warn("No Superset database connection for tenant '{}' — skipping dataset sync for '{}'",
                        tenantSlug, collectionName);
                return;
            }

            String datasetName = "kelta_" + collectionName;
            String sql = buildDatasetSql(tenantId, tenantSlug, collectionId, collectionName);

            // Check if dataset already exists
            var existing = apiClient.listDatasets(dbId).stream()
                    .filter(d -> datasetName.equals(d.get("table_name")))
                    .findFirst();

            if (existing.isPresent()) {
                log.debug("Dataset '{}' already exists for tenant '{}' — skipping", datasetName, tenantSlug);
                return;
            }

            int datasetId = apiClient.createDataset(dbId, datasetName, sql);
            if (datasetId > 0) {
                log.info("Created Superset dataset '{}' for tenant '{}' (datasetId={})",
                        datasetName, tenantSlug, datasetId);
            }
        } catch (Exception e) {
            log.error("Failed to sync Superset dataset for collection '{}' in tenant '{}': {}",
                    collectionName, tenantSlug, e.getMessage());
        }
    }

    /**
     * Lists datasets available for a tenant.
     */
    public List<Map<String, Object>> listDatasets(String tenantSlug) {
        try {
            int dbId = apiClient.findDatabaseId(tenantSlug);
            if (dbId <= 0) {
                return List.of();
            }
            return apiClient.listDatasets(dbId);
        } catch (Exception e) {
            log.error("Failed to list Superset datasets for tenant '{}': {}", tenantSlug, e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds the virtual SQL for a dataset, excluding HIDDEN fields.
     */
    private String buildDatasetSql(String tenantId, String tenantSlug,
                                    String collectionId, String collectionName) {
        // Get all fields for the collection
        List<Map<String, Object>> fields = jdbcTemplate.queryForList(
                "SELECT f.name FROM field f WHERE f.collection_id = ? ORDER BY f.name",
                collectionId
        );

        // Get HIDDEN fields (fields marked HIDDEN in any profile for this tenant)
        List<String> hiddenFields = jdbcTemplate.queryForList(
                "SELECT DISTINCT f.name FROM profile_field_permission pfp "
                + "JOIN field f ON f.id = pfp.field_id "
                + "WHERE pfp.collection_id = ? AND pfp.visibility = 'HIDDEN'",
                String.class,
                collectionId
        );

        // Build column list excluding hidden fields
        List<String> columns = new ArrayList<>();
        columns.add("id");
        columns.add("created_at");
        columns.add("updated_at");
        columns.add("created_by");

        for (Map<String, Object> field : fields) {
            String fieldName = (String) field.get("name");
            if (!hiddenFields.contains(fieldName)) {
                columns.add("\"" + fieldName + "\"");
            }
        }

        String columnList = String.join(", ", columns);
        return String.format("SELECT %s FROM \"%s\".\"%s\"", columnList, tenantSlug, collectionName);
    }
}
