package io.kelta.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kelta.auth.model.KeltaSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_PREFIX = "kelta:auth:sso:";
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SessionService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String createSession(KeltaSession session) {
        String sessionId = UUID.randomUUID().toString();
        session.setCreatedAt(Instant.now());

        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, json, SESSION_TTL);
            log.debug("Created SSO session {} for user {}", sessionId, session.getEmail());
            return sessionId;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize session", e);
        }
    }

    public Optional<KeltaSession> getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        String json = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (json == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, KeltaSession.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize session {}", sessionId, e);
            return Optional.empty();
        }
    }

    public void deleteSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            redisTemplate.delete(SESSION_PREFIX + sessionId);
            log.debug("Deleted SSO session {}", sessionId);
        }
    }
}
