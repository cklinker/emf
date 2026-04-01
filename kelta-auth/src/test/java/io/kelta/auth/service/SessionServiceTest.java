package io.kelta.auth.service;

import tools.jackson.databind.ObjectMapper;
import io.kelta.auth.model.KeltaSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SessionService sessionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sessionService = new SessionService(redisTemplate, objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void createSession_returnsSessionId() {
        KeltaSession session = KeltaSession.builder()
                .email("user@test.com")
                .tenantId("tenant-1")
                .authSource("internal")
                .groups(List.of())
                .build();

        String sessionId = sessionService.createSession(session);

        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
        verify(valueOps).set(
                startsWith("kelta:auth:sso:"),
                anyString(),
                any(Duration.class)
        );
    }

    @Test
    void getSession_returnsSessionWhenExists() throws Exception {
        KeltaSession session = KeltaSession.builder()
                .email("user@test.com")
                .tenantId("tenant-1")
                .authSource("internal")
                .groups(List.of())
                .build();

        String json = objectMapper.writeValueAsString(session);
        when(valueOps.get("kelta:auth:sso:test-id")).thenReturn(json);

        var result = sessionService.getSession("test-id");

        assertTrue(result.isPresent());
        assertEquals("user@test.com", result.get().getEmail());
    }

    @Test
    void getSession_returnsEmptyForMissingSession() {
        when(valueOps.get("kelta:auth:sso:missing")).thenReturn(null);

        var result = sessionService.getSession("missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void getSession_returnsEmptyForNullId() {
        var result = sessionService.getSession(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteSession_deletesFromRedis() {
        sessionService.deleteSession("test-id");
        verify(redisTemplate).delete("kelta:auth:sso:test-id");
    }
}
