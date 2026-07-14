package com.weedrice.whiteboard.global.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> executorContext = MDC.getCopyOfContextMap();
            try {
                setContext(callerContext);
                runnable.run();
            } finally {
                setContext(executorContext);
            }
        };
    }

    private void setContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
