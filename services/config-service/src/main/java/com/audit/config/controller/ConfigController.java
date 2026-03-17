package com.audit.config.controller;

import com.audit.config.service.IConfigService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final IConfigService configService;

    public ConfigController(IConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/threshold")
    public Map<String, Object> threshold() {
        return configService.getThresholdConfig();
    }
}
