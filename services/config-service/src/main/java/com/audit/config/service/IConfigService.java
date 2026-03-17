package com.audit.config.service;

import java.util.Map;

/**
 * 配置管理业务接口
 */
public interface IConfigService {

    /**
     * 获取审计阈值配置
     * @return 包含 overdueDays, minRate, warningChannel 的配置 Map
     */
    Map<String, Object> getThresholdConfig();
}
