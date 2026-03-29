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
                    workerApiClient.createFields(tenantId, userId, collectionId, fields);
                    log.info("Created {} fields for collection {}", fields.size(), collectionId);
                } catch (Exception e) {
                    log.error("Failed to create fields for collection {}: {}", collectionId, e.getMessage());
                    result.put("_fieldError", e.getMessage());
                }
            } else {
                log.error("Could not extract collection ID from response: {}", result);
            }
        }

        return result;
    }

    private Map<String, Object> applyLayoutProposal(String tenantId, String userId, Map<String, Object> data) {
        return workerApiClient.createPageLayout(tenantId, userId, data);
    }
}
