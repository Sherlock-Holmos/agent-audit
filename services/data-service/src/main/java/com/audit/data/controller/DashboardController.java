package com.audit.data.controller;

import com.audit.data.application.IDashboardApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
/**
 * 仪表板查询接口。
 */
public class DashboardController {

    private final IDashboardApplicationService dashboardApplicationService;

    public DashboardController(IDashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Object> dashboard(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return ApiResponse.success("ok", dashboardApplicationService.dashboard(username, fusionTaskId));
    }

    @GetMapping("/trend")
    public ApiResponse<Object> trend(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return ApiResponse.success("ok", dashboardApplicationService.trend(username, fusionTaskId));
    }

    @GetMapping("/heatmap")
    public ApiResponse<Object> heatmap(
        @RequestHeader(value = "X-User-Name", required = false) String username,
        @RequestParam(required = false) Long fusionTaskId
    ) {
        return ApiResponse.success("ok", dashboardApplicationService.heatmap(username, fusionTaskId));
    }

    @GetMapping("/dashboard/fusion-options")
    public ApiResponse<Object> fusionOptions(
        @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        return ApiResponse.success("ok", dashboardApplicationService.listFusionOptions(username));
    }
}

