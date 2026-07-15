package com.weedrice.whiteboard.domain.search.repository;

import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class SearchStatisticRepositoryTest {

    @Autowired
    private SearchStatisticRepository searchStatisticRepository;

    @Test
    @DisplayName("인기 검색어 집계는 합산 순으로 정렬하고 상위 N개만 반환한다")
    void findPopularKeywords_returnsTopKeywordsWithinLimit() {
        searchStatisticRepository.save(SearchStatistic.builder()
                .keyword("Alpha")
                .searchDate(LocalDate.of(2026, 4, 15))
                .initialSearchCount(3)
                .build());
        searchStatisticRepository.save(SearchStatistic.builder()
                .keyword("alpha")
                .searchDate(LocalDate.of(2026, 4, 16))
                .initialSearchCount(4)
                .build());
        searchStatisticRepository.save(SearchStatistic.builder()
                .keyword("beta")
                .searchDate(LocalDate.of(2026, 4, 16))
                .initialSearchCount(10)
                .build());
        searchStatisticRepository.save(SearchStatistic.builder()
                .keyword("gamma")
                .searchDate(LocalDate.of(2026, 4, 16))
                .initialSearchCount(2)
                .build());

        List<SearchStatisticRepository.PopularKeywordProjection> results = searchStatisticRepository.findPopularKeywords(
                LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 4, 16),
                PageRequest.of(0, 2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getKeyword()).isEqualTo("beta");
        assertThat(results.get(0).getCount()).isEqualTo(10L);
        assertThat(results.get(1).getKeyword()).isEqualTo("alpha");
        assertThat(results.get(1).getCount()).isEqualTo(7L);
    }
}
