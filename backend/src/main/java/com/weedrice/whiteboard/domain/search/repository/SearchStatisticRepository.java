package com.weedrice.whiteboard.domain.search.repository;

import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SearchStatisticRepository extends JpaRepository<SearchStatistic, Long> {
    Optional<SearchStatistic> findByKeywordAndSearchDate(String keyword, LocalDate searchDate);

    @Query("SELECT s.keyword, SUM(s.searchCount) as totalSearchCount " +
            "FROM SearchStatistic s " +
            "WHERE s.searchDate BETWEEN :startDate AND :endDate " +
            "GROUP BY s.keyword " +
            "ORDER BY totalSearchCount DESC")
    List<Object[]> findPopularKeywords(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
