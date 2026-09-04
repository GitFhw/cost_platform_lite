package com.ruoyi.lite.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 轻量宿主所需的最小线程池。
 *
 * <p>Bean 名称使用 costLite 前缀，避免与客户应用自己的线程池发生名称冲突。</p>
 */
@Configuration
public class CostLiteThreadPoolConfig {
    @Bean(name = "costLiteThreadPoolTaskExecutor")
    @ConditionalOnMissingBean(name = "costLiteThreadPoolTaskExecutor")
    public ThreadPoolTaskExecutor costLiteThreadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("cost-lite-run-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean(name = "costLiteScheduledExecutorService", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "costLiteScheduledExecutorService")
    public ScheduledExecutorService costLiteScheduledExecutorService() {
        return new ScheduledThreadPoolExecutor(
                2,
                runnable -> {
                    Thread thread = new Thread(runnable, "cost-lite-schedule");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
