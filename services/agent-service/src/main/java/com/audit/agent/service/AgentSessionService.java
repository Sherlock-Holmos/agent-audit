package main.java.com.audit.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentSessionService {

    private static final String RATE_LIMIT_KEY_PREFIX = "agent:rl:";
    private static final String SESSION_KEY_PREFIX = "agent:session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int sessionTtlMinutes;
    private final int maxSessionTurns;
    private final int rateLimitPerMinute;

    public AgentSessionService(
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper,
        @Value("${app.agent.session-ttl-minutes:360}") int sessionTtlMinutes,
        @Value("${app.agent.max-session-turns:20}") int maxSessionTurns,
        @Value("${app.agent.rate-limit-per-minute:30}") int rateLimitPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.sessionTtlMinutes = sessionTtlMinutes;
        this.maxSessionTurns = maxSessionTurns;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public boolean tryAcquireQuota(String userName) {
        String key = RATE_LIMIT_KEY_PREFIX + safeUser(userName);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return true;
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            return count <= rateLimitPerMinute;
        } catch (Exception ignore) {
            // Redis unavailable, degrade to allow requests.
            return true;
        }
    }

    public List<Map<String, Object>> getRecentHistory(String userName) {
        String key = SESSION_KEY_PREFIX + safeUser(userName);
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignore) {
            return List.of();
        }
    }

    public void appendTurn(String userName, String question, String answer) {
        List<Map<String, Object>> turns = new ArrayList<>(getRecentHistory(userName));
        Map<String, Object> turn = new HashMap<>();
        turn.put("q", question);
        turn.put("a", answer);
        turn.put("ts", Instant.now().toString());
        turns.add(turn);

        while (turns.size() > maxSessionTurns) {
            turns.remove(0);
        }

        String key = SESSION_KEY_PREFIX + safeUser(userName);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(turns), Duration.ofMinutes(sessionTtlMinutes));
        } catch (Exception ignore) {
            // Ignore cache failure.
        }
    }

    private String safeUser(String userName) {
        if (userName == null || userName.isBlank()) {
            return "anonymous";
        }
        return userName.trim().toLowerCase();
    }
}
