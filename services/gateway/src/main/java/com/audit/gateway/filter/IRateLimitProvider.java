package com.audit.gateway.filter;

/**
 * 限流提供者接口
 */
public interface IRateLimitProvider {

    /**
     * 检查用户是否超过限流阈值
     * @param username 用户名
     * @param limitPerMinute 每分钟限制数
     * @return 是否超过限制
     */
    boolean isExceeded(String username, int limitPerMinute);

    /**
     * 检查 IP 是否超过限流阈值
     * @param clientIp 客户端 IP
     * @param limitPerMinute 每分钟限制数
     * @return 是否超过限制
     */
    boolean isIpExceeded(String clientIp, int limitPerMinute);

    /**
     * 获取用户的当前请求计数
     * @param username 用户名
     * @return 当前计数
     */
    int getUserCount(String username);

    /**
     * 获取 IP 的当前请求计数
     * @param clientIp 客户端 IP
     * @return 当前计数
     */
    int getIpCount(String clientIp);
}
