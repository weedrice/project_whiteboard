package com.weedrice.whiteboard.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SearchStatisticCommandService {

    private final SearchStatisticWriteService searchStatisticWriteService;

    public void recordSearchStatistic(String keyword, LocalDate searchDate) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }

        String normalizedKeyword = SearchKeywordNormalizer.normalize(keyword);
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
