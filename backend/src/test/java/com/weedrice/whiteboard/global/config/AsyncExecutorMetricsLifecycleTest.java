package com.weedrice.whiteboard.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExecutorMetricsLifecycleTest {

    @ParameterizedTest
    @ValueSource(strings = {"durable", "notification", "stream", "observability"})
    void gaugesTrackManagedPoolThroughWorkAndGarbageCollection(String executorName) throws InterruptedException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            String prefix = "app.async." + executorName;
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("async-test", Map.of(
                    prefix + ".core-size", 1,
                    prefix + ".max-size", 1,
                    prefix + ".queue-capacity", 2,
                    "app.async.await-termination-seconds", 5)));
            context.registerBean(MeterRegistry.class, SimpleMeterRegistry::new);
            context.register(AsyncConfig.class);
            context.refresh();

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            ThreadPoolTaskExecutor executor = context.getBean(executorName + "TaskExecutor", ThreadPoolTaskExecutor.class);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch queuedTaskCompleted = new CountDownLatch(1);

            assertGauges(registry, executorName, 0, 0, 2);
            try {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                executor.execute(queuedTaskCompleted::countDown);

                // This also fails deterministically if gauges observe an obsolete pool,
                // even on JVMs that ignore explicit GC requests.
                assertGauges(registry, executorName, 1, 1, 1);
                System.gc();
                assertGauges(registry, executorName, 1, 1, 1);
            } finally {
                release.countDown();
            }

            assertThat(queuedTaskCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (executor.getActiveCount() != 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            System.gc();
            assertGauges(registry, executorName, 0, 0, 2);
        }
    }

    private void assertGauges(MeterRegistry registry, String executorName, int active, int depth, int remaining) {
        assertThat(gauge(registry, "noviis.async.active", executorName)).isEqualTo((double) active);
        assertThat(gauge(registry, "noviis.async.queue.depth", executorName)).isEqualTo((double) depth);
        assertThat(gauge(registry, "noviis.async.queue.remaining", executorName)).isEqualTo((double) remaining);
    }

    private double gauge(MeterRegistry registry, String name, String executorName) {
        double value = registry.get(name).tag("executor", executorName).gauge().value();
        assertThat(Double.isFinite(value)).as("%s{executor=%s} is finite", name, executorName).isTrue();
        return value;
    }
}
