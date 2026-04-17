package com.weedrice.whiteboard.domain.search.repository;

import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SearchStatisticRepository extends JpaRepository<SearchStatistic, Long> {

    interface PopularKeywordProjection {
        String getKeyword();

        Long getCount();
    }

    @Modifying
    @Query("""
            update SearchStatistic s
            set s.searchCount = s.searchCount + 1
            where s.keyword = :keyword
              and s.searchDate = :searchDate
            """)
    int incrementSearchCount(@Param("keyword") String keyword, @Param("searchDate") LocalDate searchDate);

    @Query("""
            SELECT s.keyword AS keyword, SUM(s.searchCount) AS count
            FROM SearchStatistic s
            WHERE s.searchDate BETWEEN :startDate AND :endDate
            GROUP BY s.keyword
            ORDER BY SUM(s.searchCount) DESC, s.keyword ASC
            """)
    List<PopularKeywordProjection> findPopularKeywords(@Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}
