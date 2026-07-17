package com.weedrice.whiteboard.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncExecutionPolicyTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AsyncConfig config = new AsyncConfig(new AsyncTaskProperties(), meterRegistry);
    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (executor != null) {
            executor.shutdown();
        }
        meterRegistry.close();
    }

    @Test
    void executorsUseConfiguredSizesAndQueueCapacities() {
        executor = config.durableTaskExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(4);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(200);
        assertThat(gauge("noviis.async.active", "durable")).isZero();
        assertThat(gauge("noviis.async.queue.depth", "durable")).isZero();
        assertThat(gauge("noviis.async.queue.remaining", "durable")).isEqualTo(200.0);

        executor.shutdown();
        executor = config.observabilityTaskExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
    }

    @Test
    void mdcIsCopiedToWorkerAndRestored() throws InterruptedException {
        executor = config.observabilityTaskExecutor();
        MDC.put("traceId", "trace-123");
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        executor.execute(() -> {
            workerTraceId.set(MDC.get("traceId"));
            MDC.put("traceId", "worker-mutated");
            completed.countDown();
        });

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(workerTraceId).hasValue("trace-123");
        assertThat(MDC.get("traceId")).isEqualTo("trace-123");
    }

    @Test
    void durableExecutorRejectsWithExceptionAndMetric() throws InterruptedException {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setDurable(new AsyncTaskProperties.Pool(1, 1, 0));
        executor = new AsyncConfig(properties, meterRegistry).durableTaskExecutor();
        CountDownLatch release = occupySingleThread(executor);

        assertThatThrownBy(() -> executor.execute(() -> { }))
                .isInstanceOf(TaskRejectedException.class);
        assertThat(rejectionCount("durable", "exception")).isEqualTo(1.0);
        release.countDown();
    }

    @Test
    void notificationExecutorRejectsWithoutRunningOnCaller() throws InterruptedException {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setNotification(new AsyncTaskProperties.Pool(1, 1, 0));
        executor = new AsyncConfig(properties, meterRegistry).notificationTaskExecutor();
        CountDownLatch release = occupySingleThread(executor);
        assertThatThrownBy(() -> executor.execute(() -> { }))
                .isInstanceOf(TaskRejectedException.class);
        assertThat(rejectionCount("notification", "exception")).isEqualTo(1.0);
        release.countDown();
    }

    @Test
    void observabilityExecutorDropsRejectedTaskAndRecordsMetric() throws InterruptedException {
        AsyncTaskProperties properties = new AsyncTaskProperties();
        properties.setObservability(new AsyncTaskProperties.Pool(1, 1, 0));
        executor = new AsyncConfig(properties, meterRegistry).observabilityTaskExecutor();
        CountDownLatch release = occupySingleThread(executor);
        AtomicReference<Boolean> ran = new AtomicReference<>(false);

        executor.execute(() -> ran.set(true));

        assertThat(ran).hasValue(false);
        assertThat(rejectionCount("observability", "dropped")).isEqualTo(1.0);
        release.countDown();
    }

    private CountDownLatch occupySingleThread(ThreadPoolTaskExecutor target) throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        target.execute(() -> {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        return release;
    }

    private double rejectionCount(String executorName, String outcome) {
        return meterRegistry.get("noviis.async.rejected")
                .tag("executor", executorName)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double gauge(String name, String executorName) {
        return meterRegistry.get(name)
                .tag("executor", executorName)
                .gauge()
                .value();
    }
}
