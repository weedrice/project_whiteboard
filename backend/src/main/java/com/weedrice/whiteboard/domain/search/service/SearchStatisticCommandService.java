package com.weedrice.whiteboard.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SearchStatisticCommandService {

    private final SearchStatisticWriteService searchStatisticWriteService;

    public void recordSearchStatistic(String keyword, LocalDate searchDate) {
        String canonicalKeyword = SearchRequestNormalizer.canonicalizeOptionalKeyword(keyword);
        if (canonicalKeyword == null) {
            return;
        }

        String normalizedKeyword = SearchKeywordNormalizer.normalize(canonicalKeyword);
        int updated = searchStatisticWriteService.incrementSearchCount(normalizedKeyword, searchDate);
        if (updated > 0) {
            return;
        }

        try {
            searchStatisticWriteService.createStatistic(normalizedKeyword, searchDate);
        } catch (DataIntegrityViolationException e) {
            searchStatisticWriteService.incrementSearchCount(normalizedKeyword, searchDate);
        }
    }
}
