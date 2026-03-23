package com.audit.data.service.api;

import java.util.Map;

/**
 * 异步数据处理业务接口
 */
public interface IDataProcessAsyncService {

    /**
     * 启动数据清洗任务
     * @param ownerUsername 所有者用户名
     * @param taskId 任务 ID
     * @param idempotencyKey 幂等键（用于防止重复执行）
     * @return 任务状态信息，包含 jobId 等
     */
    Map<String, Object> startCleanTask(String ownerUsername, Long taskId, String idempotencyKey);

    /**
     * 启动数据融合任务
     * @param ownerUsername 所有者用户名
     * @param taskId 任务 ID
     * @param idempotencyKey 幂等键
     * @return 任务状态信息，包含 jobId 等
     */
    Map<String, Object> startFusionTask(String ownerUsername, Long taskId, String idempotencyKey);

    /**
     * 获取任务执行状态
     * @param ownerUsername 所有者用户名
     * @param jobId 任务 ID
     * @return 任务状态详情，包含 status、result、error 等
     */
    Map<String, Object> getJobStatus(String ownerUsername, String jobId);
}

