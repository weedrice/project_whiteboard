package com.weedrice.whiteboard.global.ratelimit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CounterEventGuardTest {

    @Test
    void concurrentSuccessfulClicksRecordOnlyOnce() throws Exception {
        CounterEventGuard guard = new CounterEventGuard();
        AtomicInteger clicks = new AtomicInteger();
        var second = new FutureTask<>(() -> guard.recordOnce("ad-click", 1L, 10L, null,
                clicks::incrementAndGet, () -> 0));
        Thread contender = Thread.ofPlatform().unstarted(second);
        long firstThreadId = Thread.currentThread().threadId();
        var threads = ManagementFactory.getThreadMXBean();

        int first = guard.recordOnce("ad-click", 1L, 10L, null, () -> {
            contender.start();
            // Keep the first write in flight until the second waits for it or finishes without waiting.
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> {
                ThreadInfo state = threads.getThreadInfo(contender.threadId());
                return second.isDone() || (state != null && state.getLockOwnerId() == firstThreadId);
            });
            return clicks.incrementAndGet();
        }, () -> 0);

        assertThat(first).isOne();
        assertThat(second.get(5, TimeUnit.SECONDS)).isZero();
        assertThat(clicks.get()).isOne();
    }

    @Test
    void concurrentRequestCanRecordWhenFirstRequestFails() throws Exception {
        CounterEventGuard guard = new CounterEventGuard();
        CountDownLatch recording = new CountDownLatch(1);
        CountDownLatch retryStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var failed = executor.submit(() -> guard.recordOnce("ad-click", 1L, 10L, null, () -> {
                recording.countDown();
                await(retryStarted);
                throw new IllegalStateException("recording failed");
            }, () -> "duplicate"));
            await(recording);
            var retry = executor.submit(() -> {
                retryStarted.countDown();
                return guard.recordOnce("ad-click", 1L, 10L, null, () -> "recorded", () -> "duplicate");
            });

            assertThat(assertThrows(ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS)))
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(retry.get(5, TimeUnit.SECONDS)).isEqualTo("recorded");
            assertThat(guard.recordOnce("ad-click", 1L, 10L, null, () -> "recorded", () -> "duplicate"))
                    .isEqualTo("duplicate");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    @Test
    void tryMarkRecorded_returnsTrueOnlyForFirstEvent() {
        CounterEventGuard guard = new CounterEventGuard();

        boolean first = guard.tryMarkRecorded("post-view", 1L, 10L, "203.0.113.10");
        boolean second = guard.tryMarkRecorded("post-view", 1L, 10L, "203.0.113.10");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(guard.isRecentlyRecorded("post-view", 1L, 10L, "203.0.113.10")).isTrue();
    }

    @Test
    void tryMarkRecorded_separatesAnonymousIpActors() {
        CounterEventGuard guard = new CounterEventGuard();

        assertThat(guard.tryMarkRecorded("post-view", 1L, null, "203.0.113.10")).isTrue();
        assertThat(guard.tryMarkRecorded("post-view", 1L, null, "203.0.113.11")).isTrue();
        assertThat(guard.tryMarkRecorded("post-view", 1L, null, "203.0.113.10")).isFalse();
    }
}
