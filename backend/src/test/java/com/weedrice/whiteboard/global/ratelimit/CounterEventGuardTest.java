package com.weedrice.whiteboard.global.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CounterEventGuardTest {

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
