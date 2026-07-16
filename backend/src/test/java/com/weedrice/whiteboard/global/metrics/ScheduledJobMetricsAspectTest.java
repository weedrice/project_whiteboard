package com.weedrice.whiteboard.global.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledJobMetricsAspectTest {

    @Test
    void recordsSuccessWithDedicatedScheduledJobTag() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledJobMetricsAspect aspect = new ScheduledJobMetricsAspect(registry);
        ProceedingJoinPoint joinPoint = joinPointReturning("done");

        assertThat(aspect.measure(joinPoint)).isEqualTo("done");

        Timer timer = registry.get("noviis.scheduler.execution")
                .tag("scheduled_job", "Object.refresh")
                .tag("outcome", "success")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("job")).isNull();
    }

    @Test
    void recordsErrorAndRethrows() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ScheduledJobMetricsAspect aspect = new ScheduledJobMetricsAspect(registry);
        ProceedingJoinPoint joinPoint = joinPointThrowing(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.measure(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        Timer timer = registry.get("noviis.scheduler.execution")
                .tag("scheduled_job", "Object.refresh")
                .tag("outcome", "error")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
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
