package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import org.springframework.data.domain.Pageable;

import java.util.Locale;

public final class AnonymousCacheKey {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private AnonymousCacheKey() {
    }

    public static String normalizePeriod(String period) {
        String normalized = period == null ? "" : period.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "7d", "30d" -> normalized;
            default -> "24h";
        };
    }

    public static String trending(Pageable pageable, String period) {
        int page = pageable != null && pageable.isPaged() ? Math.max(pageable.getPageNumber(), 0) : 0;
        int requestedSize = pageable != null && pageable.isPaged() ? pageable.getPageSize() : DEFAULT_PAGE_SIZE;
        int size = Math.min(Math.max(requestedSize, 1), PageRequestUtils.DEFAULT_MAX_PAGE_SIZE);
        return normalizePeriod(period) + ":" + page + ":" + size;
    }
}
