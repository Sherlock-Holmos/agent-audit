package com.audit.config.controller;

import com.audit.config.application.IConfigApplicationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final IConfigApplicationService configApplicationService;

    public ConfigController(IConfigApplicationService configApplicationService) {
        this.configApplicationService = configApplicationService;
    }

    @GetMapping("/threshold")
    public Map<String, Object> threshold() {
        return configApplicationService.getThresholdConfig();
    }
}
