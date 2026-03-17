package com.audit.gateway.filter;

import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final List<String> WHITE_LIST = List.of(
        "/api/auth/login",
        "/api/auth/register",
        "/actuator/health",
        "/actuator/info",
        "/actuator/prometheus"
    );

    private final ITokenProvider tokenProvider;
    private final IRateLimitProvider rateLimitProvider;
    private final int userRateLimitPerMinute;
    private final int ipRateLimitPerMinute;

    public JwtAuthFilter(
        ITokenProvider tokenProvider,
        IRateLimitProvider rateLimitProvider,
        @Value("${app.gateway.rate-limit-per-minute:120}") int userRateLimitPerMinute,
        @Value("${app.gateway.ip-rate-limit-per-minute:240}") int ipRateLimitPerMinute
    ) {
        this.tokenProvider = tokenProvider;
        this.rateLimitProvider = rateLimitProvider;
        this.userRateLimitPerMinute = Math.max(userRateLimitPerMinute, 1);
        this.ipRateLimitPerMinute = Math.max(ipRateLimitPerMinute, 1);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        ServerHttpRequest traceMutated = request.mutate().header(TRACE_ID_HEADER, traceId).build();
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);

        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange.mutate().request(traceMutated).build());
        }

        String clientIp = extractClientIp(traceMutated);
        if (rateLimitProvider.isIpExceeded(clientIp, ipRateLimitPerMinute)) {
            return tooManyRequests(exchange, "访问过于频繁，请稍后重试");
        }

        String auth = traceMutated.getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少或非法Authorization头");
        }

        String token = auth.substring(7);
        String userName;
        String userRole;
        try {
            userName = tokenProvider.validateAndExtractUsername(token);
            userRole = tokenProvider.extractRole(token);
        } catch (JwtException ex) {
            return unauthorized(exchange, "Token无效或已过期");
        }

        if (rateLimitProvider.isExceeded(userName, userRateLimitPerMinute)) {
            return tooManyRequests(exchange, "用户请求过于频繁，请稍后重试");
        }

        ServerHttpRequest mutated = traceMutated.mutate()
            .header("X-User-Name", userName)
            .header("X-User-Role", userRole)
            .header("X-User-Dept", "audit-dept-001")
            .build();

        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", message);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("X-Rate-Limit-Error", message);
        exchange.getResponse().getHeaders().add("Retry-After", "60");
        return exchange.getResponse().setComplete();
    }

    private String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (request.getRemoteAddress() == null || request.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
