package com.audit.config.application;

import com.audit.config.service.IConfigService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConfigApplicationService implements IConfigApplicationService {

    private final IConfigService configService;

    public ConfigApplicationService(IConfigService configService) {
        this.configService = configService;
    }

    @Override
    public Map<String, Object> getThresholdConfig() {
        return configService.getThresholdConfig();
    }
}
