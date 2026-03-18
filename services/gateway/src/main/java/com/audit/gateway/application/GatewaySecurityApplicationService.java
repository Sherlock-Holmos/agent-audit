package com.audit.gateway.application;

import com.audit.gateway.filter.IRateLimitProvider;
import com.audit.gateway.filter.ITokenProvider;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GatewaySecurityApplicationService implements IGatewaySecurityApplicationService {

    private static final List<String> WHITE_LIST = List.of(
        "/api/auth/login",
        "/api/auth/register",
        "/actuator/health",
        "/actuator/info",
        "/actuator/prometheus"
    );

    private final ITokenProvider tokenProvider;
    private final IRateLimitProvider rateLimitProvider;

    public GatewaySecurityApplicationService(ITokenProvider tokenProvider, IRateLimitProvider rateLimitProvider) {
        this.tokenProvider = tokenProvider;
        this.rateLimitProvider = rateLimitProvider;
    }

    @Override
    public String ensureTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }

    @Override
    public boolean isWhitelisted(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    @Override
    public boolean isIpRateLimited(String clientIp, int ipRateLimitPerMinute) {
        return rateLimitProvider.isIpExceeded(clientIp, ipRateLimitPerMinute);
    }

    @Override
    public GatewayAuthContext authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("缺少或非法Authorization头");
        }
        String token = authorizationHeader.substring(7);
        try {
            String username = tokenProvider.validateAndExtractUsername(token);
            String role = tokenProvider.extractRole(token);
            return new GatewayAuthContext(username, role);
        } catch (JwtException ex) {
            throw ex;
        }
    }

    @Override
    public boolean isUserRateLimited(String username, int userRateLimitPerMinute) {
        return rateLimitProvider.isExceeded(username, userRateLimitPerMinute);
    }
}
