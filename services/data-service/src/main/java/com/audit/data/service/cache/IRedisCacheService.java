package com.audit.data.service.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * 缓存管理业务接口
 */
public interface IRedisCacheService {

    /**
     * 获取缓存值或按需计算
     * @param key 缓存键
     * @param ttl 缓存有效期
     * @param typeReference 返回值类型引用
     * @param supplier 值计算函数（缓存不存在时调用）
     * @return 缓存值或计算结果
     * @param <T> 返回值类型
     */
    <T> T getOrCompute(String key, Duration ttl, TypeReference<T> typeReference, Supplier<T> supplier);

    /**
     * 删除指定前缀的所有缓存键
     * @param prefix 缓存键前缀
     */
    void evictByPrefix(String prefix);
}

