package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchDateRangeResolverTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    @ParameterizedTest
    @MethodSource("periodRanges")
    void resolvesPeriodWithInclusiveCalendarDates(
            String period,
            String from,
            String to,
            LocalDateTime expectedFrom,
            LocalDateTime expectedTo) {
        var range = SearchDateRangeResolver.resolve(period, from, to, TODAY);

        assertThat(range.from()).isEqualTo(expectedFrom);
        assertThat(range.to()).isEqualTo(expectedTo);
    }

    private static Stream<Arguments> periodRanges() {
        return Stream.of(
                Arguments.of(null, null, null, null, null),
                Arguments.of("TODAY", null, null,
                        LocalDateTime.of(2026, 7, 31, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0)),
                Arguments.of("WEEK", null, null,
                        LocalDateTime.of(2026, 7, 25, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0)),
                Arguments.of("MONTH", null, null,
                        LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0)),
                Arguments.of("CUSTOM", "2026-07-10", "2026-07-20",
                        LocalDateTime.of(2026, 7, 10, 0, 0), LocalDateTime.of(2026, 7, 21, 0, 0)),
                Arguments.of("CUSTOM", null, "2026-07-20",
                        null, LocalDateTime.of(2026, 7, 21, 0, 0)),
                Arguments.of("CUSTOM", "2026-07-10", null,
                        LocalDateTime.of(2026, 7, 10, 0, 0), null));
    }

    @ParameterizedTest
    @MethodSource("invalidRanges")
    void rejectsInvalidRange(String period, String from, String to) {
        assertThatThrownBy(() -> SearchDateRangeResolver.resolve(period, from, to, TODAY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    private static Stream<Arguments> invalidRanges() {
        return Stream.of(
                Arguments.of("YEAR", null, null),
                Arguments.of("CUSTOM", "2026-07-20", "2026-07-10"));
    }

    @Test
    void customRangeUsesRequestDateParser() {
        assertThatThrownBy(() -> SearchDateRangeResolver.resolve(
                "CUSTOM", "2026/07/10", "2026-07-20", TODAY))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
}
