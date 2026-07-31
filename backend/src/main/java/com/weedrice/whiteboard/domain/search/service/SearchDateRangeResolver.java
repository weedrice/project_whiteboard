package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

final class SearchDateRangeResolver {
    private SearchDateRangeResolver() {
    }

    static SearchDateRange resolve(String period, String from, String to) {
        return resolve(period, from, to, DateTimeUtils.nowKST().toLocalDate());
    }

    static SearchDateRange resolve(String period, String from, String to, LocalDate today) {
        String normalizedPeriod = SearchRequestNormalizer.normalizeSearchPeriod(period);
        if (normalizedPeriod == null) {
            return SearchDateRange.empty();
        }

        return switch (normalizedPeriod) {
            case "TODAY" -> SearchDateRange.between(today, today);
            case "WEEK" -> SearchDateRange.between(today.minusWeeks(1).plusDays(1), today);
            case "MONTH" -> SearchDateRange.between(today.minusMonths(1).plusDays(1), today);
            case "CUSTOM" -> customRange(from, to);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

    private static SearchDateRange customRange(String from, String to) {
        LocalDate startDate = SearchRequestNormalizer.parseOptionalDate(from);
        LocalDate endDate = SearchRequestNormalizer.parseOptionalDate(to);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return SearchDateRange.between(startDate, endDate);
    }

    record SearchDateRange(LocalDateTime from, LocalDateTime to) {
        static SearchDateRange empty() {
            return new SearchDateRange(null, null);
        }

        static SearchDateRange between(LocalDate from, LocalDate to) {
            LocalDateTime start = from == null ? null : from.atStartOfDay();
            LocalDateTime endExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
            return new SearchDateRange(start, endExclusive);
        }
    }
}
