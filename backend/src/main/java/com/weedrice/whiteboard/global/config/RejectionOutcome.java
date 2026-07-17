package com.weedrice.whiteboard.global.config;

enum RejectionOutcome {
    EXCEPTION("exception"),
    DROPPED("dropped");

    private final String metricTag;

    RejectionOutcome(String metricTag) {
        this.metricTag = metricTag;
    }

    String metricTag() {
        return metricTag;
    }
}
