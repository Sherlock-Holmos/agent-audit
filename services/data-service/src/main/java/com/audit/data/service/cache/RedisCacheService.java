package com.audit.data.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * Redis 缓存服务：提供通用的读穿缓存能力与按前缀失效能力。
 */
public class RedisCacheService implements IRedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter writeCounter;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.hitCounter = Counter.builder("audit.cache.hit").register(meterRegistry);
        this.missCounter = Counter.builder("audit.cache.miss").register(meterRegistry);
        this.writeCounter = Counter.builder("audit.cache.write").register(meterRegistry);
    }

    @SuppressWarnings("null")
    public <T> T getOrCompute(String key, Duration ttl, TypeReference<T> typeReference, Supplier<T> supplier) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                hitCounter.increment();
                return objectMapper.readValue(cached, typeReference);
            }
        } catch (Exception ignore) {
            // Redis unavailable or cache payload broken, fallback to direct computation.
        }

        missCounter.increment();

        T value = supplier.get();

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            writeCounter.increment();
        } catch (Exception ignore) {
            // Ignore caching failure to avoid affecting business path.
        }

        return value;
    }

    public void evictByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys == null || keys.isEmpty()) return;
            redisTemplate.delete(keys);
        } catch (Exception ignore) {
            // Ignore cache eviction failure.
        }
    }
}


