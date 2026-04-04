package io.kelta.ai.controller;

import io.kelta.ai.service.ProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalController")
class ProposalControllerTest {

    @Mock
    private ProposalService proposalService;

    private ProposalController controller;

    @BeforeEach
    void setUp() {
        controller = new ProposalController(proposalService);
    }

    @Test
    @DisplayName("applies proposal successfully")
    void appliesProposal() {
        UUID proposalId = UUID.randomUUID();
        Map<String, Object> result = Map.of("collectionId", "abc-123", "fieldsCreated", 5);

        when(proposalService.applyProposal(proposalId, "tenant-1", "user-1"))
                .thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
                controller.applyProposal("tenant-1", "user-1", proposalId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("applied");
        assertThat(response.getBody().get("data")).isEqualTo(result);
    }

    @Test
    @DisplayName("includes warnings in response when present")
    void includesWarnings() {
        UUID proposalId = UUID.randomUUID();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collectionId", "abc-123");
        result.put("_warnings", List.of("Field 'foo' skipped"));

        when(proposalService.applyProposal(proposalId, "tenant-1", "user-1"))
                .thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
                controller.applyProposal("tenant-1", "user-1", proposalId);

        assertThat(response.getBody().get("warnings")).isEqualTo(List.of("Field 'foo' skipped"));
    }

    @Test
    @DisplayName("returns 400 for invalid proposal")
    void returns400ForInvalidProposal() {
        UUID proposalId = UUID.randomUUID();
        when(proposalService.applyProposal(proposalId, "tenant-1", "user-1"))
                .thenThrow(new IllegalArgumentException("Proposal not found"));

        ResponseEntity<Map<String, Object>> response =
                controller.applyProposal("tenant-1", "user-1", proposalId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
        assertThat(errors.getFirst().get("code")).isEqualTo("INVALID_PROPOSAL");
    }

    @Test
    @DisplayName("returns 400 for already-applied proposal")
    void returns400ForAlreadyApplied() {
        UUID proposalId = UUID.randomUUID();
        when(proposalService.applyProposal(proposalId, "tenant-1", "user-1"))
                .thenThrow(new IllegalStateException("Already applied"));

        ResponseEntity<Map<String, Object>> response =
                controller.applyProposal("tenant-1", "user-1", proposalId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
        assertThat(errors.getFirst().get("code")).isEqualTo("PROPOSAL_ALREADY_APPLIED");
    }

    @Test
    @DisplayName("returns 500 for unexpected errors")
    void returns500ForUnexpectedError() {
        UUID proposalId = UUID.randomUUID();
        when(proposalService.applyProposal(proposalId, "tenant-1", "user-1"))
                .thenThrow(new RuntimeException("Connection failed"));

        ResponseEntity<Map<String, Object>> response =
                controller.applyProposal("tenant-1", "user-1", proposalId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
        assertThat(errors.getFirst().get("code")).isEqualTo("APPLY_FAILED");
    }
}
