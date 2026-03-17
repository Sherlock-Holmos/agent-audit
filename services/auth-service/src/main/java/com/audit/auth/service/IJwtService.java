package com.audit.auth.service;

/**
 * JWT 令牌管理接口
 */
public interface IJwtService {

    /**
     * 生成 JWT 令牌
     * @param username 用户名
     * @param role 用户角色
     * @return JWT 令牌字符串
     */
    String generateToken(String username, String role);

    /**
     * 从 JWT 令牌中解析用户名
     * @param token JWT 令牌字符串
     * @return 用户名
     * @throws io.jsonwebtoken.JwtException 当 token 无效或过期时
     */
    String parseUsername(String token);
}
