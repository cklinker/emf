package io.kelta.ai.service.tools.handlers;

import io.kelta.ai.model.AiProposal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProposeUpdateFieldHandler")
class ProposeUpdateFieldHandlerTest {

    private final ProposeUpdateFieldHandler handler = new ProposeUpdateFieldHandler();

    @Test
    @DisplayName("accepts metadata-only changes")
    void acceptsMetadataChanges() {
        AiProposal proposal = handler.buildProposal(Map.of(
                "collectionName", "products",
                "fieldName", "price",
                "changes", Map.of("displayName", "List Price", "required", true)
        ));
        assertThat(proposal.type()).isEqualTo("update_field");
        assertThat(proposal.status()).isEqualTo("pending");
    }

    @Test
    @DisplayName("rejects type changes with a clear message")
    void rejectsTypeChange() {
        assertThatThrownBy(() -> handler.buildProposal(Map.of(
                "collectionName", "products",
                "fieldName", "price",
                "changes", Map.of("type", "STRING")
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type changes are not supported")
                .hasMessageContaining("propose_remove_field");
    }
}
