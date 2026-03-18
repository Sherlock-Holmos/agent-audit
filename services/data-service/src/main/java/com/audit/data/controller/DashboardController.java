package com.audit.data.controller;

import com.audit.data.application.IDashboardApplicationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DashboardController {

    private final IDashboardApplicationService dashboardApplicationService;

    public DashboardController(IDashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return dashboardApplicationService.dashboard(username, fusionTaskId);
    }

    @GetMapping("/trend")
    public Map<String, Object> trend(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return dashboardApplicationService.trend(username, fusionTaskId);
    }

    @GetMapping("/heatmap")
    public Map<String, Object> heatmap(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return dashboardApplicationService.heatmap(username, fusionTaskId);
    }

    @GetMapping("/dashboard/fusion-options")
    public Map<String, Object> fusionOptions(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return Map.of(
            "code", 0,
            "message", "ok",
            "data", dashboardApplicationService.listFusionOptions(username)
        );
    }
}
