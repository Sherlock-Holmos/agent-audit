package com.audit.data.application;

import com.audit.data.service.IDashboardService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
/**
 * 仪表板应用服务：处理用户上下文并编排领域仪表板查询。
 */
public class DashboardApplicationService implements IDashboardApplicationService {

    private final IDashboardService dashboardService;

    public DashboardApplicationService(IDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(String username, Long fusionTaskId) {
        return dashboardService.buildDashboard(normalizeUser(username), fusionTaskId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> trend(String username, Long fusionTaskId) {
        return dashboardService.buildTrend(normalizeUser(username), fusionTaskId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> heatmap(String username, Long fusionTaskId) {
        return dashboardService.buildHeatmap(normalizeUser(username), fusionTaskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFusionOptions(String username) {
        return dashboardService.listFusionOptions(normalizeUser(username));
    }

    private String normalizeUser(String username) {
        return (username == null || username.isBlank()) ? "anonymous" : username;
    }
}

