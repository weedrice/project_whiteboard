package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchStatisticRepository searchStatisticRepository;
    @Mock
    private SearchPersonalizationRepository searchPersonalizationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SearchService searchService;

    @Test
    @DisplayName("검색 기록 저장 성공")
    void recordSearch_success() {
        // given
        Long userId = 1L;
        String keyword = "test";
        User user = User.builder().build();
        SearchStatistic statistic = SearchStatistic.builder().keyword(keyword).searchDate(LocalDate.now()).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(searchStatisticRepository.findByKeywordAndSearchDate(any(), any())).thenReturn(Optional.of(statistic));

        // when
        searchService.recordSearch(userId, keyword);

        // then
        verify(searchStatisticRepository).save(any(SearchStatistic.class));
        verify(searchPersonalizationRepository).save(any());
    }

    @Test
    @DisplayName("인기 검색어 조회 성공")
    void getPopularKeywords_success() {
        // given
        Object[] result1 = {"keyword1", 10L};
        Object[] result2 = {"keyword2", 5L};
        when(searchStatisticRepository.findPopularKeywords(any(), any())).thenReturn(List.of(result1, result2));

        // when
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords("WEEKLY", 10);

        // then
        assertThat(popularKeywords).hasSize(2);
        assertThat(popularKeywords.get(0).getKeyword()).isEqualTo("keyword1");
        assertThat(popularKeywords.get(0).getCount()).isEqualTo(10L);
    }
}
