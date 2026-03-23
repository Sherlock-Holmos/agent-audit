package com.audit.data.application;

import java.util.List;
import java.util.Map;

/**
 * 仪表板应用服务接口。
 */
public interface IDashboardApplicationService {

    Map<String, Object> dashboard(String username, Long fusionTaskId);

    Map<String, Object> trend(String username, Long fusionTaskId);

    Map<String, Object> heatmap(String username, Long fusionTaskId);

    List<Map<String, Object>> listFusionOptions(String username);
}

