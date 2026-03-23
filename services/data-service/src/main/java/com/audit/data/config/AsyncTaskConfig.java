package com.audit.data.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
/**
 * 异步任务线程池配置。
 */
public class AsyncTaskConfig {

    @Bean(name = "processTaskExecutor")
    public TaskExecutor processTaskExecutor(
        @Value("${app.task.core-pool-size:4}") int corePoolSize,
        @Value("${app.task.max-pool-size:8}") int maxPoolSize,
        @Value("${app.task.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("process-task-");
        executor.setCorePoolSize(Math.max(corePoolSize, 1));
        executor.setMaxPoolSize(Math.max(maxPoolSize, corePoolSize));
        executor.setQueueCapacity(Math.max(queueCapacity, 10));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

