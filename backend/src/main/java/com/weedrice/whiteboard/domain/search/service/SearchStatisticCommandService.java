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
        int updated = searchStatisticWriteService.incrementSearchCount(keyword, searchDate);
        if (updated > 0) {
            return;
        }

        try {
            searchStatisticWriteService.createStatistic(keyword, searchDate);
        } catch (DataIntegrityViolationException e) {
            searchStatisticWriteService.incrementSearchCount(keyword, searchDate);
        }
    }
}
