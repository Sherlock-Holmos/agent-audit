package com.audit.data.controller;

import com.audit.data.service.DashboardService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return dashboardService.buildDashboard(user(username), fusionTaskId);
    }

    @GetMapping("/trend")
    public Map<String, Object> trend(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return dashboardService.buildTrend(user(username), fusionTaskId);
    }

    @GetMapping("/heatmap")
    public Map<String, Object> heatmap(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return dashboardService.buildHeatmap(user(username), fusionTaskId);
    }

    @GetMapping("/dashboard/fusion-options")
    public Map<String, Object> fusionOptions(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return Map.of(
            "code", 0,
            "message", "ok",
            "data", dashboardService.listFusionOptions(user(username))
        );
    }

    private String user(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username;
    }
}
