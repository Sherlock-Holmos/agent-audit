package com.audit.data.service;

import java.util.List;
import java.util.Map;

/**
 * 仪表板业务接口
 */
public interface IDashboardService {

    /**
     * 获取融合任务选项列表
     * @param ownerUsername 所有者用户名
     * @return 融合任务选项列表
     */
    List<Map<String, Object>> listFusionOptions(String ownerUsername);

    /**
     * 构建仪表板数据
     * @param ownerUsername 所有者用户名
     * @param fusionTaskId 融合任务 ID
     * @return 仪表板数据，包含完成率、逾期数等指标
     */
    Map<String, Object> buildDashboard(String ownerUsername, Long fusionTaskId);

    /**
     * 构建趋势数据
     * @param ownerUsername 所有者用户名
     * @param fusionTaskId 融合任务 ID
     * @return 包含日期、完成率和预测率的趋势数据
     */
    Map<String, Object> buildTrend(String ownerUsername, Long fusionTaskId);

    /**
     * 构建热力图数据
     * @param ownerUsername 所有者用户名
     * @param fusionTaskId 融合任务 ID
     * @return 包含部门、指标和完成率的热力图数据
     */
    Map<String, Object> buildHeatmap(String ownerUsername, Long fusionTaskId);

    /**
     * 清除用户的仪表板缓存
     * @param ownerUsername 所有者用户名
     */
    void invalidateOwnerCache(String ownerUsername);
}

