package com.audit.config.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @GetMapping("/threshold")
    public Map<String, Object> threshold() {
        return Map.of(
            "overdueDays", 7,
            "minRate", 85,
            "warningChannel", "system"
        );
    }
}
