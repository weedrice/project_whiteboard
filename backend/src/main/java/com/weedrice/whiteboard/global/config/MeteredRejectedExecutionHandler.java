package com.weedrice.whiteboard.global.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

final class MeteredRejectedExecutionHandler implements RejectedExecutionHandler {

    private final RejectionOutcome outcome;
    private final Counter counter;

    MeteredRejectedExecutionHandler(
            String executorName,
            RejectionOutcome outcome,
            MeterRegistry meterRegistry) {
        this.outcome = outcome;
        this.counter = Counter.builder("noviis.async.rejected")
                .tag("executor", executorName)
                .tag("outcome", outcome.metricTag())
                .register(meterRegistry);
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        counter.increment();
        if (outcome == RejectionOutcome.EXCEPTION) {
            throw new RejectedExecutionException("Async task rejected by " + executor);
        }
    }
}
