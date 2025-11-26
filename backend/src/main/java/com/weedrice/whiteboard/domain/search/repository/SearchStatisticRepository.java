package com.weedrice.whiteboard.domain.search.repository;

import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SearchStatisticRepository extends JpaRepository<SearchStatistic, Long> {
    Optional<SearchStatistic> findByKeywordAndSearchDate(String keyword, LocalDate searchDate);
}
