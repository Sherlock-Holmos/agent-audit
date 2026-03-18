package com.audit.config.service;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 配置管理业务实现
 */
@Service
public class ConfigServiceImpl implements IConfigService {

    /**
     * 审计阈值常量定义
     */
    private static final int OVERDUE_DAYS = 7;
    private static final int MIN_RATE = 85;
    private static final String WARNING_CHANNEL = "system";

    @Override
    public Map<String, Object> getThresholdConfig() {
        return Map.of(
            "overdueDays", OVERDUE_DAYS,
            "minRate", MIN_RATE,
            "warningChannel", WARNING_CHANNEL
        );
    }
}

