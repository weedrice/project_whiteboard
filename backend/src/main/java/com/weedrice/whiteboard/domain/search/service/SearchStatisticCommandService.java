package com.weedrice.whiteboard.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SearchStatisticCommandService {

    private final SearchStatisticWriteService searchStatisticWriteService;
    private final SearchUpsertRetryPolicy searchUpsertRetryPolicy;

    public void recordSearchStatistic(String keyword, LocalDate searchDate) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        if (canonicalKeyword == null) {
            return;
        }

        String normalizedKeyword = SearchKeywordNormalizer.normalize(canonicalKeyword);
        searchUpsertRetryPolicy.updateOrCreate(
                () -> searchStatisticWriteService.incrementSearchCount(normalizedKeyword, searchDate),
                () -> searchStatisticWriteService.createStatistic(normalizedKeyword, searchDate));
    }
}
