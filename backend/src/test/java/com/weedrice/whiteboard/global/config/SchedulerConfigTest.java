package com.weedrice.whiteboard.global.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerConfigTest {

    @Test
    void blockedSemanticSchedulerDoesNotDelayHeartbeatScheduler() throws Exception {
        SchedulerConfig config = new SchedulerConfig();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskScheduler common = config.taskScheduler(4, registry);
        ThreadPoolTaskScheduler semantic = config.semanticSearchTaskScheduler(registry);
        ThreadPoolTaskScheduler heartbeat = config.heartbeatTaskScheduler(registry);
        ThreadPoolTaskScheduler webPush = config.webPushTaskScheduler(registry);
        common.initialize();
        semantic.initialize();
        heartbeat.initialize();
        webPush.initialize();
        CountDownLatch semanticStarted = new CountDownLatch(1);
        CountDownLatch releaseSemantic = new CountDownLatch(1);
        CountDownLatch heartbeatRan = new CountDownLatch(1);

        try {
            semantic.schedule(() -> {
                semanticStarted.countDown();
                try {
                    releaseSemantic.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }, Instant.now());
            assertThat(semanticStarted.await(2, TimeUnit.SECONDS)).isTrue();

            heartbeat.schedule(heartbeatRan::countDown, Instant.now());

            assertThat(heartbeatRan.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(common.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
            assertThat(semantic.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
            assertThat(heartbeat.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
            assertThat(webPush.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
        } finally {
            releaseSemantic.countDown();
            common.shutdown();
            semantic.shutdown();
            heartbeat.shutdown();
            webPush.shutdown();
        }
    }
}
