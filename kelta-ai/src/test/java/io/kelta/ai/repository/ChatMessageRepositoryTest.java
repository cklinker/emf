package io.kelta.ai.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageRepository")
class ChatMessageRepositoryTest {

    private static final String TENANT = "tenant-1";

    @Mock
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("findMostRecentAssistantTokensInput returns the most recent assistant turn's tokens_input")
    void returnsRecordedTokensInput() {
        UUID conversationId = UUID.randomUUID();
        ChatMessageRepository repository = new ChatMessageRepository(jdbc, objectMapper);

        when(jdbc.query(anyString(), any(RowMapper.class), eq(conversationId), eq(TENANT)))
                .thenReturn(List.of(87_432));

        int result = repository.findMostRecentAssistantTokensInput(conversationId, TENANT);

        assertThat(result).isEqualTo(87_432);
    }

    @Test
    @DisplayName("findMostRecentAssistantTokensInput returns 0 when the conversation has no assistant turns yet")
    void returnsZeroWhenNoAssistantTurns() {
        UUID conversationId = UUID.randomUUID();
        ChatMessageRepository repository = new ChatMessageRepository(jdbc, objectMapper);

        when(jdbc.query(anyString(), any(RowMapper.class), eq(conversationId), eq(TENANT)))
                .thenReturn(List.of());

        int result = repository.findMostRecentAssistantTokensInput(conversationId, TENANT);

        assertThat(result).isZero();
    }
}
