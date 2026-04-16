package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class SearchStatisticWriteService {

    private final SearchStatisticRepository searchStatisticRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int incrementSearchCount(String keyword, LocalDate searchDate) {
        return searchStatisticRepository.incrementSearchCount(keyword, searchDate);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createStatistic(String keyword, LocalDate searchDate) {
        SearchStatistic statistic = SearchStatistic.builder()
                .keyword(keyword)
                .searchDate(searchDate)
                .initialSearchCount(1)
                .build();
        searchStatisticRepository.saveAndFlush(statistic);
    }
}
