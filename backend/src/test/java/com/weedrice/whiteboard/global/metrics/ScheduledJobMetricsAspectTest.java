package com.weedrice.whiteboard.global.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledJobMetricsAspectTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:34:56Z");

    @Test
    void recordsSuccessWithDedicatedScheduledJobTag() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledJobMetricsAspect aspect = new ScheduledJobMetricsAspect(
                registry, Clock.fixed(NOW, ZoneOffset.UTC));
        ProceedingJoinPoint joinPoint = joinPointReturning("done");

        assertThat(aspect.measure(joinPoint)).isEqualTo("done");

        Timer timer = registry.get("noviis.scheduler.execution")
                .tag("scheduled_job", "Object.refresh")
                .tag("outcome", "success")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("job")).isNull();
        assertThat(registry.get("noviis.scheduler.last.success.timestamp")
                .tag("scheduled_job", "Object.refresh")
                .gauge()
                .value()).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    void recordsErrorAndRethrows() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledJobMetricsAspect aspect = new ScheduledJobMetricsAspect(
                registry, Clock.fixed(NOW, ZoneOffset.UTC));
        ProceedingJoinPoint joinPoint = joinPointThrowing(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.measure(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        Timer timer = registry.get("noviis.scheduler.execution")
                .tag("scheduled_job", "Object.refresh")
                .tag("outcome", "error")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(registry.find("noviis.scheduler.last.success.timestamp")
                .tag("scheduled_job", "Object.refresh")
                .gauge()).isNull();
    }

    @Test
    void failedRunDoesNotOverwritePreviousSuccessTimestamp() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledJobMetricsAspect aspect = new ScheduledJobMetricsAspect(
                registry, Clock.fixed(NOW, ZoneOffset.UTC));

        aspect.measure(joinPointReturning("done"));

        assertThatThrownBy(() -> aspect.measure(joinPointThrowing(new IllegalStateException("boom"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("noviis.scheduler.last.success.timestamp")
                .tag("scheduled_job", "Object.refresh")
                .gauge()
                .value()).isEqualTo(NOW.getEpochSecond());
    }

    private ProceedingJoinPoint joinPointReturning(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = baseJoinPoint();
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private ProceedingJoinPoint joinPointThrowing(Throwable throwable) throws Throwable {
        ProceedingJoinPoint joinPoint = baseJoinPoint();
        when(joinPoint.proceed()).thenThrow(throwable);
        return joinPoint;
    }

    private ProceedingJoinPoint baseJoinPoint() {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("refresh");
        return joinPoint;
    }
}
