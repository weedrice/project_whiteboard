package com.weedrice.whiteboard.domain.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchStatisticCommandServiceTest {

    @Mock
    private SearchStatisticWriteService searchStatisticWriteService;

    @InjectMocks
    private SearchStatisticCommandService searchStatisticCommandService;

    @Test
    @DisplayName("기존 통계가 있으면 search_count만 증가시킨다")
    void recordSearchStatistic_incrementsExistingStatistic() {
        when(searchStatisticWriteService.incrementSearchCount(eq("test"), any(LocalDate.class))).thenReturn(1);

        searchStatisticCommandService.recordSearchStatistic("  Test  ", LocalDate.now());

        verify(searchStatisticWriteService).incrementSearchCount(eq("test"), any(LocalDate.class));
        verify(searchStatisticWriteService, never()).createStatistic(eq("test"), any(LocalDate.class));
    }

    @Test
    @DisplayName("search statistic keyword is capped at 255 characters")
    void recordSearchStatistic_truncatesKeyword() {
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);
        String normalizedKeyword = "a".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH);

        searchStatisticCommandService.recordSearchStatistic(keyword, LocalDate.now());

        verify(searchStatisticWriteService).incrementSearchCount(eq(normalizedKeyword), any(LocalDate.class));
    }

    @Test
    @DisplayName("통계 생성 충돌 시 새 트랜잭션으로 다시 증가시킨다")
    void recordSearchStatistic_retriesIncrementAfterDuplicateInsert() {
        when(searchStatisticWriteService.incrementSearchCount(eq("test"), any(LocalDate.class)))
                .thenReturn(0)
                .thenReturn(1);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(searchStatisticWriteService)
                .createStatistic(eq("test"), any(LocalDate.class));

        searchStatisticCommandService.recordSearchStatistic("test", LocalDate.now());

        verify(searchStatisticWriteService, times(2)).incrementSearchCount(eq("test"), any(LocalDate.class));
        verify(searchStatisticWriteService).createStatistic(eq("test"), any(LocalDate.class));
    }

    @Test
    @DisplayName("빈 검색어는 통계 기록을 건너뛴다")
    void recordSearchStatistic_ignoresBlankKeyword() {
        searchStatisticCommandService.recordSearchStatistic("   ", LocalDate.now());

        verifyNoInteractions(searchStatisticWriteService);
    }
}
