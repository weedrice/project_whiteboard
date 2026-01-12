package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
import com.weedrice.whiteboard.domain.search.entity.SearchStatistic;
import com.weedrice.whiteboard.domain.search.repository.SearchPersonalizationRepository;
import com.weedrice.whiteboard.domain.search.repository.SearchStatisticRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchStatisticRepository searchStatisticRepository;
    @Mock
    private SearchPersonalizationRepository searchPersonalizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private FileService fileService;

    @InjectMocks
    private SearchService searchService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testuser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Test
    @DisplayName("검색 기록 저장 성공")
    void recordSearch_success() {
        // given
        Long userId = 1L;
        String keyword = "test";
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
    @DisplayName("검색 기록 저장 성공 - userId가 null인 경우")
    void recordSearch_success_withNullUserId() {
        // given
        String keyword = "test";
        SearchStatistic statistic = SearchStatistic.builder().keyword(keyword).searchDate(LocalDate.now()).build();

        when(searchStatisticRepository.findByKeywordAndSearchDate(any(), any())).thenReturn(Optional.of(statistic));

        // when
        searchService.recordSearch(null, keyword);

        // then
        verify(searchStatisticRepository).save(any(SearchStatistic.class));
        verify(searchPersonalizationRepository, never()).save(any());
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

    @Test
    @DisplayName("인기 검색어 조회 성공 - DAILY")
    void getPopularKeywords_success_daily() {
        // given
        Object[] result1 = {"keyword1", 10L};
        when(searchStatisticRepository.findPopularKeywords(any(), any())).thenReturn(List.<Object[]>of(result1));

        // when
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords("DAILY", 10);

        // then
        assertThat(popularKeywords).hasSize(1);
    }

    @Test
    @DisplayName("인기 검색어 조회 성공 - MONTHLY")
    void getPopularKeywords_success_monthly() {
        // given
        Object[] result1 = {"keyword1", 10L};
        when(searchStatisticRepository.findPopularKeywords(any(), any())).thenReturn(List.<Object[]>of(result1));

        // when
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords("MONTHLY", 10);

        // then
        assertThat(popularKeywords).hasSize(1);
    }

    @Test
    @DisplayName("최근 검색어 조회 성공")
    void getRecentSearches_success() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        SearchPersonalization personalization = SearchPersonalization.builder()
                .user(user)
                .keyword("test")
                .build();
        Page<SearchPersonalization> page = new PageImpl<>(List.of(personalization), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(searchPersonalizationRepository.findByUserOrderByCreatedAtDesc(eq(user), eq(pageable)))
                .thenReturn(page);

        // when
        SearchPersonalizationResponse response = searchService.getRecentSearches(userId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("최근 검색어 삭제 성공")
    void deleteRecentSearch_success() {
        // given
        Long userId = 1L;
        Long logId = 1L;
        SearchPersonalization personalization = SearchPersonalization.builder()
                .user(user)
                .keyword("test")
                .build();
        ReflectionTestUtils.setField(personalization, "logId", logId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(searchPersonalizationRepository.findById(logId)).thenReturn(Optional.of(personalization));

        // when
        searchService.deleteRecentSearch(userId, logId);

        // then
        verify(searchPersonalizationRepository).delete(personalization);
    }

    @Test
    @DisplayName("모든 최근 검색어 삭제 성공")
    void deleteAllRecentSearches_success() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        searchService.deleteAllRecentSearches(userId);

        // then
        verify(searchPersonalizationRepository).deleteByUser(user);
    }
}
