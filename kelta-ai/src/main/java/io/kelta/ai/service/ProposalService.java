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
        // Extract fields from the proposal data
        List<Map<String, Object>> fields = (List<Map<String, Object>>) data.get("fields");

        // Create the collection first (without fields)
        Map<String, Object> collectionData = new java.util.LinkedHashMap<>(data);
        collectionData.remove("fields");

        Map<String, Object> result = workerApiClient.createCollection(tenantId, userId, collectionData);
        log.info("Collection created: {}", result);

        // Then create the fields if any
        if (fields != null && !fields.isEmpty()) {
            // Extract the collection ID from the JSON:API response
            // Response format: { "data": { "id": "...", "type": "collections", "attributes": {...} } }
            String collectionId = null;
            Object dataObj = result.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> resultData = (Map<String, Object>) dataObj;
                collectionId = resultData.get("id") != null ? String.valueOf(resultData.get("id")) : null;
            }
            log.info("Extracted collection ID: {} from response", collectionId);

            if (collectionId != null) {
                try {
                    // Pre-create global picklists for PICKLIST/MULTI_PICKLIST fields
                    createPicklistsForFields(tenantId, userId, fields);

                    workerApiClient.createFields(tenantId, userId, collectionId, fields);
                    log.info("Created {} fields for collection {}", fields.size(), collectionId);
                } catch (Exception e) {
                    log.error("Failed to create fields for collection {}: {}", collectionId, e.getMessage());
                    result.put("_fieldError", e.getMessage());
                }

                // Auto-generate a default DETAIL layout with all fields
                try {
                    createDefaultLayout(tenantId, userId, collectionId,
                            (String) data.getOrDefault("displayName", data.get("name")));
                } catch (Exception e) {
                    log.error("Failed to create default layout for collection {}: {}", collectionId, e.getMessage());
                }

                // Add collection to the navigation menu
                try {
                    addToNavigationMenu(tenantId, userId,
                            (String) data.get("name"),
                            (String) data.getOrDefault("displayName", data.get("name")));
                } catch (Exception e) {
                    log.error("Failed to add menu item for collection {}: {}", collectionId, e.getMessage());
                }
            } else {
                log.error("Could not extract collection ID from response: {}", result);
            }
        }

        return result;
    }

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
        Object layoutDataObj = layoutResult.get("data");
        String layoutId = null;
        if (layoutDataObj instanceof Map) {
            layoutId = String.valueOf(((Map<String, Object>) layoutDataObj).get("id"));
        }
        if (layoutId == null) {
            log.error("Could not extract layout ID from response");
            return;
        }
        log.info("Created layout {} for collection {}", layoutId, collectionId);

        // 2. Create a section
        Map<String, Object> sectionData = new java.util.LinkedHashMap<>();
        sectionData.put("layoutId", layoutId);
        sectionData.put("heading", "Details");
        sectionData.put("columns", 2);
        sectionData.put("sortOrder", 0);
        sectionData.put("style", "DEFAULT");

        Map<String, Object> sectionResult = workerApiClient.createLayoutSection(tenantId, userId, sectionData);
        Object sectionDataObj = sectionResult.get("data");
        String sectionId = null;
        if (sectionDataObj instanceof Map) {
            sectionId = String.valueOf(((Map<String, Object>) sectionDataObj).get("id"));
        }
        if (sectionId == null) {
            log.error("Could not extract section ID from response");
            return;
        }
        log.info("Created section {} for layout {}", sectionId, layoutId);

        // 3. Fetch the created fields to get their IDs
        List<Map<String, Object>> createdFields = workerApiClient.listFields(tenantId, collectionId);
        log.info("Found {} fields for layout placement", createdFields.size());

        // 4. Place each field in the section
        int sortOrder = 0;
        for (Map<String, Object> field : createdFields) {
            String fieldId = String.valueOf(field.get("id"));
            try {
                Map<String, Object> fieldPlacement = new java.util.LinkedHashMap<>();
                fieldPlacement.put("sectionId", sectionId);
                fieldPlacement.put("fieldId", fieldId);
                fieldPlacement.put("sortOrder", sortOrder++);
                workerApiClient.createLayoutField(tenantId, userId, fieldPlacement);
            } catch (Exception e) {
                log.warn("Failed to place field {} in layout: {}", fieldId, e.getMessage());
            }
        }
        log.info("Placed {} fields in layout {}", sortOrder, layoutId);
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
    private void createPicklistsForFields(String tenantId, String userId, List<Map<String, Object>> fields) {
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
                log.error("Failed to create global picklist for field '{}': {}", fieldName, e.getMessage());
            }
        }
    }

    private Map<String, Object> applyLayoutProposal(String tenantId, String userId, Map<String, Object> data) {
        return workerApiClient.createPageLayout(tenantId, userId, data);
    }
}
