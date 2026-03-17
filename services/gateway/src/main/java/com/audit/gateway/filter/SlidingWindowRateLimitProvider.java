package com.audit.gateway.filter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 滑动窗口限流提供者实现
 */
@Component
public class SlidingWindowRateLimitProvider implements IRateLimitProvider {

    private static final long WINDOW_MS = Duration.ofMinutes(1).toMillis();
    private final Map<String, WindowCounter> userCounters = new ConcurrentHashMap<>();
    private final Map<String, WindowCounter> ipCounters = new ConcurrentHashMap<>();

    @Override
    public boolean isExceeded(String username, int limitPerMinute) {
        return checkLimit(userCounters, username, limitPerMinute);
    }

    @Override
    public boolean isIpExceeded(String clientIp, int limitPerMinute) {
        return checkLimit(ipCounters, clientIp, limitPerMinute);
    }

    @Override
    public int getUserCount(String username) {
        WindowCounter counter = userCounters.get(username);
        return counter != null ? counter.count.get() : 0;
    }

    @Override
    public int getIpCount(String clientIp) {
        WindowCounter counter = ipCounters.get(clientIp);
        return counter != null ? counter.count.get() : 0;
    }

    /**
     * 检查是否超过限制
     */
    private boolean checkLimit(Map<String, WindowCounter> counters, String key, int maxAllowed) {
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));
        synchronized (counter) {
            if (now - counter.windowStartMs >= WINDOW_MS) {
                counter.windowStartMs = now;
                counter.count.set(0);
            }
            return counter.count.incrementAndGet() > maxAllowed;
        }
    }

    /**
     * 滑动窗口计数器
     */
    private static final class WindowCounter {
        private long windowStartMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private WindowCounter(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }
}
