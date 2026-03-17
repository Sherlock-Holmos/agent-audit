package com.audit.auth.service;

import java.util.Map;

/**
 * 用户认证业务接口
 */
public interface IAuthUserService {

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @return 用户信息
     * @throws IllegalArgumentException 当用户名为空或已存在时
     */
    Map<String, Object> register(String username, String password);

    /**
     * 用户认证
     * @param username 用户名
     * @param password 密码
     * @return 用户信息
     * @throws IllegalStateException 当用户名或密码错误或用户已停用时
     * @throws IllegalArgumentException 当用户名和密码为空时
     */
    Map<String, Object> authenticate(String username, String password);

    /**
     * 根据用户名获取用户信息
     * @param username 用户名
     * @return 用户信息
     * @throws IllegalArgumentException 当用户不存在或已停用时
     */
    Map<String, Object> getProfileByUsername(String username);

    /**
     * 更新用户信息
     * @param username 用户名
     * @param payload 包含 nickname, avatarUrl, email, phone, department 的 Map
     * @return 更新后的用户信息
     * @throws IllegalArgumentException 当用户不存在或参数无效时
     */
    Map<String, Object> updateProfile(String username, Map<String, Object> payload);

    /**
     * 停用用户
     * @param username 用户名
     * @throws IllegalArgumentException 当用户不存在时
     */
    void deactivate(String username);
}
