package com.ruoyi.lite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 轻量宿主所需的最小线程池。
 *
 * <p>完整若依后台中的线程池配置不在轻量扫描范围内，因此在宿主内提供
 * 同名 Bean，保证母体运行服务可以独立装配。</p>
 */
@Configuration
public class CostLiteThreadPoolConfig {
    @Bean(name = "threadPoolTaskExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
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

    @Bean(name = "scheduledExecutorService", destroyMethod = "shutdown")
    public ScheduledExecutorService scheduledExecutorService() {
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
