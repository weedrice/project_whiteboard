package com.weedrice.whiteboard.domain.search.event;

import java.time.LocalDate;

public record SearchRecordedEvent(Long userId, String keyword, LocalDate searchDate) {
}
