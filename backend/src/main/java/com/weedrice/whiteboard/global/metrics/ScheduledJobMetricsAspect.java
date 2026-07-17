package com.weedrice.whiteboard.global.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Aspect
@Component
public class ScheduledJobMetricsAspect {

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ConcurrentMap<String, AtomicLong> lastSuccessEpochSeconds = new ConcurrentHashMap<>();

    public ScheduledJobMetricsAspect(MeterRegistry meterRegistry, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        Timer.Sample sample = Timer.start(meterRegistry);
        String job = joinPoint.getTarget().getClass().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        String outcome = "success";
        try {
            Object result = joinPoint.proceed();
            recordLastSuccess(job);
            return result;
        } catch (Throwable throwable) {
            outcome = "error";
            throw throwable;
        } finally {
            sample.stop(Timer.builder("noviis.scheduler.execution")
                    .tag("scheduled_job", job)
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    private void recordLastSuccess(String job) {
        long epochSecond = clock.instant().getEpochSecond();
        AtomicLong timestamp = lastSuccessEpochSeconds.computeIfAbsent(
                job, ignored -> registerLastSuccessGauge(job, epochSecond));
        timestamp.set(epochSecond);
    }

    private AtomicLong registerLastSuccessGauge(String job, long epochSecond) {
        AtomicLong timestamp = new AtomicLong(epochSecond);
        Gauge.builder(
                        "noviis.scheduler.last.success.timestamp",
                        timestamp,
                        AtomicLong::get)
                .description("Unix timestamp of the scheduled job's last successful completion")
                .baseUnit("seconds")
                .tag("scheduled_job", job)
                .register(meterRegistry);
        return timestamp;
    }
}
