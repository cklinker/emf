package io.kelta.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.ai.model.AiProposal;
import io.kelta.ai.model.ChatMessage;
import io.kelta.ai.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages AI proposals - storing, retrieving, and applying them
 * via the worker API.
 */
@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final ChatMessageRepository messageRepository;
    private final WorkerApiClient workerApiClient;
    private final ObjectMapper objectMapper;

    public ProposalService(ChatMessageRepository messageRepository, WorkerApiClient workerApiClient,
                            ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.workerApiClient = workerApiClient;
        this.objectMapper = objectMapper;
    }

    public AiProposal createProposal(String type, Map<String, Object> data) {
        return AiProposal.pending(type, data);
    }

    public String serializeProposal(AiProposal proposal) {
        try {
            return objectMapper.writeValueAsString(proposal);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize proposal: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize proposal", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> applyProposal(UUID proposalId, String tenantId, String userId) {
        log.info("Applying proposal {} for tenant {}", proposalId, tenantId);

        // Find the message containing this proposal
        ChatMessage message = messageRepository.findByProposalId(proposalId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Proposal not found: " + proposalId));

        AiProposal proposal;
        try {
            proposal = objectMapper.readValue(message.proposalJson(), AiProposal.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize proposal", e);
        }

        if (!"pending".equals(proposal.status())) {
            throw new IllegalStateException("Proposal has already been " + proposal.status());
        }

        return switch (proposal.type()) {
            case "collection" -> applyCollectionProposal(tenantId, userId, proposal.data());
            case "layout" -> applyLayoutProposal(tenantId, userId, proposal.data());
            default -> throw new IllegalArgumentException("Unknown proposal type: " + proposal.type());
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyCollectionProposal(String tenantId, String userId, Map<String, Object> data) {
        List<String> warnings = new java.util.ArrayList<>();

        // Extract fields from the proposal data
        List<Map<String, Object>> fields = (List<Map<String, Object>>) data.get("fields");

        // Create the collection first (without fields)
        Map<String, Object> collectionData = new java.util.LinkedHashMap<>(data);
        collectionData.remove("fields");

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.putAll(workerApiClient.createCollection(tenantId, userId, collectionData));
        log.info("Collection created successfully");

        // Then create the fields if any
        if (fields != null && !fields.isEmpty()) {
            String collectionId = null;
            Object dataObj = result.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> resultData = (Map<String, Object>) dataObj;
                collectionId = resultData.get("id") != null ? String.valueOf(resultData.get("id")) : null;
            }
            log.info("Extracted collection ID: {} from response", collectionId);

            if (collectionId != null) {
                // Pre-create global picklists for PICKLIST/MULTI_PICKLIST fields
                List<String> picklistErrors = createPicklistsForFields(tenantId, userId, fields);
                warnings.addAll(picklistErrors);

                // Create fields
                List<String> fieldErrors = workerApiClient.createFields(tenantId, userId, collectionId, fields);
                warnings.addAll(fieldErrors);
                log.info("Field creation completed with {} warnings", fieldErrors.size());

                // Auto-generate a default DETAIL layout with all fields
                try {
                    createDefaultLayout(tenantId, userId, collectionId,
                            (String) data.getOrDefault("displayName", data.get("name")));
                } catch (Exception e) {
                    String msg = "Failed to create layout: " + e.getMessage();
                    log.error(msg);
                    warnings.add(msg);
                }

                // Add collection to the navigation menu
                try {
                    addToNavigationMenu(tenantId, userId,
                            (String) data.get("name"),
                            (String) data.getOrDefault("displayName", data.get("name")));
                } catch (Exception e) {
                    String msg = "Failed to add menu item: " + e.getMessage();
                    log.error(msg);
                    warnings.add(msg);
                }
            } else {
                warnings.add("Could not extract collection ID from response");
                log.error("Could not extract collection ID from response: {}", result);
            }
        }

        if (!warnings.isEmpty()) {
            result.put("_warnings", warnings);
        }

        return result;
    }

    // Field types that should go in a "Details" section (short, key info)
    private static final java.util.Set<String> KEY_FIELD_TYPES = java.util.Set.of(
            "STRING", "EMAIL", "PHONE", "URL", "EXTERNAL_ID", "AUTO_NUMBER",
            "PICKLIST", "BOOLEAN", "DATE", "DATETIME", "INTEGER", "DOUBLE",
            "CURRENCY", "PERCENT");

    // Field types that need their own section (long content)
    private static final java.util.Set<String> LONG_CONTENT_TYPES = java.util.Set.of(
            "RICH_TEXT", "JSON");

    @SuppressWarnings("unchecked")
    private void createDefaultLayout(String tenantId, String userId, String collectionId, String displayName) {
        log.info("Creating default DETAIL layout for collection {}", collectionId);

        // 1. Create the page layout
        Map<String, Object> layoutData = new java.util.LinkedHashMap<>();
        layoutData.put("collectionId", collectionId);
        layoutData.put("name", displayName + " Detail");
        layoutData.put("layoutType", "DETAIL");
        layoutData.put("isDefault", true);

        Map<String, Object> layoutResult = workerApiClient.createPageLayout(tenantId, userId, layoutData);
        String layoutId = extractId(layoutResult);
        if (layoutId == null) {
            log.error("Could not extract layout ID from response");
            return;
        }
        log.info("Created layout {} for collection {}", layoutId, collectionId);

        // 2. Fetch the created fields
        List<Map<String, Object>> createdFields = workerApiClient.listFields(tenantId, collectionId);
        log.info("Found {} fields for layout placement", createdFields.size());

        if (createdFields.isEmpty()) return;

        // 3. Categorize fields into groups
        List<Map<String, Object>> keyFields = new java.util.ArrayList<>();
        List<Map<String, Object>> relationshipFields = new java.util.ArrayList<>();
        List<Map<String, Object>> longContentFields = new java.util.ArrayList<>();
        List<Map<String, Object>> metadataFields = new java.util.ArrayList<>();

        for (Map<String, Object> field : createdFields) {
            Map<String, Object> attrs = (Map<String, Object>) field.getOrDefault("attributes", field);
            String type = String.valueOf(attrs.getOrDefault("type", "STRING")).toUpperCase();
            String name = String.valueOf(attrs.getOrDefault("name", ""));

            // Metadata fields (dates, status, flags at the bottom)
            if (name.contains("created") || name.contains("updated") || name.contains("modified")
                    || name.equals("is_active") || name.equals("active") || name.equals("status")) {
                metadataFields.add(field);
            } else if ("MASTER_DETAIL".equals(type) || "LOOKUP".equals(type) || "REFERENCE".equals(type)) {
                relationshipFields.add(field);
            } else if (LONG_CONTENT_TYPES.contains(type) || "MULTI_PICKLIST".equals(type)) {
                longContentFields.add(field);
            } else {
                keyFields.add(field);
            }
        }

        int sectionOrder = 0;

        // Section 1: Key Details (2 columns) — name, type, short fields
        if (!keyFields.isEmpty() || !relationshipFields.isEmpty()) {
            List<Map<String, Object>> detailFields = new java.util.ArrayList<>();
            detailFields.addAll(keyFields);
            detailFields.addAll(relationshipFields);

            String sectionId = createSection(tenantId, userId, layoutId, displayName + " Information", 2, sectionOrder++, "DEFAULT");
            if (sectionId != null) {
                placeFieldsInSection(tenantId, userId, sectionId, detailFields);
            }
        }

        // Section 2: Description / Long Content (1 column, collapsible)
        if (!longContentFields.isEmpty()) {
            String sectionId = createSection(tenantId, userId, layoutId, "Details", 1, sectionOrder++, "DEFAULT");
            if (sectionId != null) {
                placeFieldsInSection(tenantId, userId, sectionId, longContentFields);
            }
        }

        // Section 3: System / Metadata (2 columns, collapsible)
        if (!metadataFields.isEmpty()) {
            String sectionId = createSection(tenantId, userId, layoutId, "System Information", 2, sectionOrder++, "COLLAPSIBLE");
            if (sectionId != null) {
                placeFieldsInSection(tenantId, userId, sectionId, metadataFields);
            }
        }

        log.info("Created {} sections for layout {}", sectionOrder, layoutId);
    }

    @SuppressWarnings("unchecked")
    private String createSection(String tenantId, String userId, String layoutId,
                                  String heading, int columns, int sortOrder, String style) {
        Map<String, Object> sectionData = new java.util.LinkedHashMap<>();
        sectionData.put("layoutId", layoutId);
        sectionData.put("heading", heading);
        sectionData.put("columns", columns);
        sectionData.put("sortOrder", sortOrder);
        sectionData.put("style", style);

        Map<String, Object> result = workerApiClient.createLayoutSection(tenantId, userId, sectionData);
        return extractId(result);
    }

    private void placeFieldsInSection(String tenantId, String userId, String sectionId,
                                       List<Map<String, Object>> fields) {
        int sortOrder = 0;
        for (Map<String, Object> field : fields) {
            String fieldId = String.valueOf(field.get("id"));
            try {
                Map<String, Object> placement = new java.util.LinkedHashMap<>();
                placement.put("sectionId", sectionId);
                placement.put("fieldId", fieldId);
                placement.put("sortOrder", sortOrder++);
                workerApiClient.createLayoutField(tenantId, userId, placement);
            } catch (Exception e) {
                log.warn("Failed to place field {} in layout: {}", fieldId, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extractId(Map<String, Object> response) {
        Object dataObj = response.get("data");
        if (dataObj instanceof Map) {
            Object id = ((Map<String, Object>) dataObj).get("id");
            return id != null ? String.valueOf(id) : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void addToNavigationMenu(String tenantId, String userId, String collectionName, String displayName) {
        log.info("Adding {} to navigation menu", collectionName);

        // Find existing menus
        List<Map<String, Object>> menus = workerApiClient.listMenus(tenantId);
        String menuId;

        if (menus.isEmpty()) {
            // Create a default menu
            Map<String, Object> menuData = new java.util.LinkedHashMap<>();
            menuData.put("name", "Main");
            menuData.put("description", "Main navigation menu");
            menuData.put("displayOrder", 0);
            Map<String, Object> menuResult = workerApiClient.createMenu(tenantId, userId, menuData);
            Object menuDataObj = menuResult.get("data");
            menuId = (menuDataObj instanceof Map) ? String.valueOf(((Map<String, Object>) menuDataObj).get("id")) : null;
            log.info("Created default menu with ID {}", menuId);
        } else {
            // Use the first existing menu
            Map<String, Object> firstMenu = menus.getFirst();
            menuId = String.valueOf(firstMenu.get("id"));
            log.info("Using existing menu {}", menuId);
        }

        if (menuId == null) {
            log.error("Could not determine menu ID");
            return;
        }

        // Create menu item
        Map<String, Object> itemData = new java.util.LinkedHashMap<>();
        itemData.put("menuId", menuId);
        itemData.put("label", displayName);
        itemData.put("path", "/resources/" + collectionName);
        itemData.put("icon", "database");
        itemData.put("displayOrder", menus.isEmpty() ? 0 : 100); // Add at end
        itemData.put("active", true);

        workerApiClient.createMenuItem(tenantId, userId, itemData);
        log.info("Added menu item '{}' -> /resources/{}", displayName, collectionName);
    }

    /**
     * For PICKLIST and MULTI_PICKLIST fields, create global picklists from
     * the enumValues array and set the fieldTypeConfig.globalPicklistId.
     */
    @SuppressWarnings("unchecked")
    private List<String> createPicklistsForFields(String tenantId, String userId, List<Map<String, Object>> fields) {
        List<String> errors = new java.util.ArrayList<>();
        for (Map<String, Object> field : fields) {
            String type = String.valueOf(field.get("type")).toUpperCase();
            if (!"PICKLIST".equals(type) && !"MULTI_PICKLIST".equals(type)) continue;

            List<String> enumValues = (List<String>) field.get("enumValues");
            if (enumValues == null || enumValues.isEmpty()) continue;

            String fieldName = String.valueOf(field.get("name"));
            try {
                // Create the global picklist
                Map<String, Object> picklistData = new java.util.LinkedHashMap<>();
                picklistData.put("name", fieldName);
                picklistData.put("sorted", false);
                picklistData.put("restricted", false);

                Map<String, Object> picklistResult = workerApiClient.createGlobalPicklist(tenantId, userId, picklistData);
                Object plData = picklistResult.get("data");
                String picklistId = (plData instanceof Map) ? String.valueOf(((Map<String, Object>) plData).get("id")) : null;

                if (picklistId == null) {
                    log.warn("Could not extract picklist ID for field '{}'", fieldName);
                    continue;
                }

                // Create the picklist values
                List<Map<String, Object>> values = new java.util.ArrayList<>();
                for (int i = 0; i < enumValues.size(); i++) {
                    String val = enumValues.get(i);
                    values.add(Map.of(
                            "value", val,
                            "label", val,
                            "isDefault", i == 0,
                            "isActive", true,
                            "sortOrder", i
                    ));
                }
                workerApiClient.createPicklistValues(tenantId, userId, picklistId, values);
                log.info("Created global picklist '{}' with {} values", fieldName, values.size());

                // Set the globalPicklistId on the field's fieldTypeConfig
                Map<String, Object> fieldTypeConfig = (Map<String, Object>) field.get("fieldTypeConfig");
                if (fieldTypeConfig == null) {
                    fieldTypeConfig = new java.util.LinkedHashMap<>();
                    field.put("fieldTypeConfig", fieldTypeConfig);
                }
                fieldTypeConfig.put("globalPicklistId", picklistId);

                // Remove enumValues since the picklist is now managed globally
                field.remove("enumValues");

            } catch (Exception e) {
                String msg = "Failed to create picklist for '" + fieldName + "': " + e.getMessage();
                log.error(msg);
                errors.add(msg);
            }
        }
        return errors;
    }

    private Map<String, Object> applyLayoutProposal(String tenantId, String userId, Map<String, Object> data) {
        return workerApiClient.createPageLayout(tenantId, userId, data);
    }
}
