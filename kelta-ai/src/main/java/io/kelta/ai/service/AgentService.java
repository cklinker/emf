package io.kelta.ai.service;

import io.kelta.ai.model.AgentDefinition;
import io.kelta.ai.repository.AgentDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD and validation for governed {@link AgentDefinition}s. The orchestration runtime (slice 6b)
 * consumes these; this service owns only their lifecycle. Tenant isolation is enforced by passing
 * {@code tenantId} into every repository call.
 */
@Service
public class AgentService {

    private static final int MAX_NAME_LENGTH = 255;

    private final AgentDefinitionRepository repository;

    public AgentService(AgentDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<AgentDefinition> list(String tenantId) {
        return repository.findByTenant(tenantId);
    }

    public Optional<AgentDefinition> get(String tenantId, UUID id) {
        return repository.findById(id, tenantId);
    }

    public AgentDefinition create(String tenantId, String userId, AgentUpsertRequest request) {
        validate(request);
        AgentDefinition agent = AgentDefinition.create(
                tenantId,
                request.name().trim(),
                request.description(),
                request.systemPrompt(),
                blankToNull(request.model()),
                request.maxTokens(),
                normalizeTools(request.allowedTools()),
                request.monthlyTokenBudget(),
                request.enabled() == null || request.enabled(),
                userId);
        repository.save(agent);
        return agent;
    }

    public Optional<AgentDefinition> update(String tenantId, String userId, UUID id,
                                            AgentUpsertRequest request) {
        validate(request);
        Optional<AgentDefinition> existing = repository.findById(id, tenantId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        AgentDefinition updated = existing.get().withUpdates(
                request.name().trim(),
                request.description(),
                request.systemPrompt(),
                blankToNull(request.model()),
                request.maxTokens(),
                normalizeTools(request.allowedTools()),
                request.monthlyTokenBudget(),
                request.enabled() == null || request.enabled(),
                userId);
        repository.save(updated);
        return Optional.of(updated);
    }

    public boolean delete(String tenantId, UUID id) {
        return repository.deleteById(id, tenantId);
    }

    private void validate(AgentUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (isBlank(request.name())) {
            throw new IllegalArgumentException("'name' is required");
        }
        if (request.name().trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("'name' must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (isBlank(request.systemPrompt())) {
            throw new IllegalArgumentException("'systemPrompt' is required");
        }
        if (request.maxTokens() != null && request.maxTokens() <= 0) {
            throw new IllegalArgumentException("'maxTokens' must be positive");
        }
        if (request.monthlyTokenBudget() != null && request.monthlyTokenBudget() < 0) {
            throw new IllegalArgumentException("'monthlyTokenBudget' must not be negative");
        }
    }

    /** Drops null/blank tool names and de-duplicates while preserving order. */
    private static List<String> normalizeTools(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        return tools.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
