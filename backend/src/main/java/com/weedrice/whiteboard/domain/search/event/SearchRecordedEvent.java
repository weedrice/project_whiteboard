package com.weedrice.whiteboard.domain.search.event;

import java.time.LocalDate;

public record SearchRecordedEvent(String keyword, LocalDate searchDate) {
}
