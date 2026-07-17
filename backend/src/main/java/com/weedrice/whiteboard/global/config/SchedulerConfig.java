package com.weedrice.whiteboard.global.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:4}") int poolSize,
            MeterRegistry meterRegistry) {
        return scheduler("common", Math.max(1, poolSize), meterRegistry);
    }

    @Bean(name = "heartbeatTaskScheduler")
    public ThreadPoolTaskScheduler heartbeatTaskScheduler(MeterRegistry meterRegistry) {
        return scheduler("heartbeat", 1, meterRegistry);
    }

    @Bean(name = "semanticSearchTaskScheduler")
    public ThreadPoolTaskScheduler semanticSearchTaskScheduler(MeterRegistry meterRegistry) {
        return scheduler("semantic-search", 1, meterRegistry);
    }

    @Bean(name = "webPushTaskScheduler")
    public ThreadPoolTaskScheduler webPushTaskScheduler(MeterRegistry meterRegistry) {
        return scheduler("web-push", 1, meterRegistry);
    }

    private ThreadPoolTaskScheduler scheduler(String name, int poolSize, MeterRegistry meterRegistry) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("noviis-" + name + "-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRemoveOnCancelPolicy(true);

        Gauge.builder("noviis.scheduler.pool.active", scheduler, ThreadPoolTaskScheduler::getActiveCount)
                .tag("pool", name)
                .register(meterRegistry);
        Gauge.builder("noviis.scheduler.pool.size", scheduler, ThreadPoolTaskScheduler::getPoolSize)
                .tag("pool", name)
                .register(meterRegistry);
        Gauge.builder(
                        "noviis.scheduler.pool.queued",
                        scheduler,
                        value -> value.getScheduledThreadPoolExecutor().getQueue().size())
                .tag("pool", name)
                .register(meterRegistry);
        return scheduler;
    }
}
