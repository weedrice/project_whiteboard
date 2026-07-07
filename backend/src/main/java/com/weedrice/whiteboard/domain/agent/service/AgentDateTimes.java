package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class AgentDateTimes {

    static final ZoneId KST = DateTimeUtils.KST_ZONE_ID;

    private AgentDateTimes() {
    }

    static LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }

    static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock);
    }

    static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return toOffsetDateTime(value, null);
    }

    static OffsetDateTime toOffsetDateTime(LocalDateTime value, LocalDateTime fallback) {
        LocalDateTime effective = value != null ? value : fallback;
        return effective == null ? null : effective.atZone(KST).toOffsetDateTime();
    }
}
