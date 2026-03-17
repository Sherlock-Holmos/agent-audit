package com.audit.gateway.filter;

/**
 * 令牌验证提供者接口
 */
public interface ITokenProvider {

    /**
     * 验证 JWT 令牌有效性
     * @param token JWT 令牌
     * @return 令牌包含的用户名
     * @throws io.jsonwebtoken.JwtException 当令牌无效或过期时
     */
    String validateAndExtractUsername(String token);

    /**
     * 从令牌中解析用户角色
     * @param token JWT 令牌
     * @return 用户角色
     */
    String extractRole(String token);
}
