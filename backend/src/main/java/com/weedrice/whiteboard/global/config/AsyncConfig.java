package com.weedrice.whiteboard.global.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
@EnableConfigurationProperties(AsyncTaskProperties.class)
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncTaskProperties properties;
    private final MeterRegistry meterRegistry;

    @Bean(name = "durableTaskExecutor")
    public ThreadPoolTaskExecutor durableTaskExecutor() {
        return createExecutor("durable", properties.getDurable(), RejectionOutcome.EXCEPTION);
    }

    @Bean(name = "notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        return createExecutor("notification", properties.getNotification(), RejectionOutcome.EXCEPTION);
    }

    @Bean(name = "streamTaskExecutor")
    public ThreadPoolTaskExecutor streamTaskExecutor() {
        return createExecutor("stream", properties.getStream(), RejectionOutcome.DROPPED);
    }

    @Bean(name = "observabilityTaskExecutor")
    public ThreadPoolTaskExecutor observabilityTaskExecutor() {
        return createExecutor("observability", properties.getObservability(), RejectionOutcome.DROPPED);
    }

    @Override
    public Executor getAsyncExecutor() {
        return durableTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, parameters) -> log.error(
                "Unhandled async exception. method={}, exceptionType={}",
                method.getName(), throwable.getClass().getSimpleName());
    }

    private ThreadPoolTaskExecutor createExecutor(
            String executorName,
            AsyncTaskProperties.Pool pool,
            RejectionOutcome rejectionOutcome) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(pool.getCoreSize());
        executor.setMaxPoolSize(pool.getMaxSize());
        executor.setQueueCapacity(pool.getQueueCapacity());
        executor.setThreadNamePrefix("async-" + executorName + "-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setRejectedExecutionHandler(new MeteredRejectedExecutionHandler(
                executorName, rejectionOutcome, meterRegistry));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();
        registerExecutorGauges(executorName, executor.getThreadPoolExecutor());
        return executor;
    }

    private void registerExecutorGauges(String executorName, ThreadPoolExecutor executor) {
        Gauge.builder("noviis.async.active", executor, ThreadPoolExecutor::getActiveCount)
                .tag("executor", executorName)
                .register(meterRegistry);
        Gauge.builder("noviis.async.queue.depth", executor, value -> value.getQueue().size())
                .tag("executor", executorName)
                .register(meterRegistry);
        Gauge.builder("noviis.async.queue.remaining", executor, value -> value.getQueue().remainingCapacity())
                .tag("executor", executorName)
                .register(meterRegistry);
    }
}
