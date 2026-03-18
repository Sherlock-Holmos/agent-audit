package com.audit.gateway.application;

public interface IGatewaySecurityApplicationService {

    String ensureTraceId(String traceId);

    boolean isWhitelisted(String path);

    boolean isIpRateLimited(String clientIp, int ipRateLimitPerMinute);

    GatewayAuthContext authenticate(String authorizationHeader);

    boolean isUserRateLimited(String username, int userRateLimitPerMinute);
}
