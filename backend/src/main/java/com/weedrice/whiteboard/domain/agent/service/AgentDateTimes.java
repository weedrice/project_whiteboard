package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

final class AgentDateTimes {

    static final ZoneId KST = DateTimeUtils.KST_ZONE_ID;

    private AgentDateTimes() {
    }

    static LocalDate today() {
        return LocalDate.now(KST);
    }

    static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return toOffsetDateTime(value, null);
    }

    static OffsetDateTime toOffsetDateTime(LocalDateTime value, LocalDateTime fallback) {
        LocalDateTime effective = value != null ? value : fallback;
        return effective == null ? null : effective.atZone(KST).toOffsetDateTime();
    }
}
