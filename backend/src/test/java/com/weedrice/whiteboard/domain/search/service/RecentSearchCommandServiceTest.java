package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecentSearchCommandServiceTest {

    @Mock
    private RecentSearchWriteService recentSearchWriteService;

    @Mock
    private SearchUserLookupPolicy searchUserLookupPolicy;

    @InjectMocks
    private RecentSearchCommandService recentSearchCommandService;

    @Test
    @DisplayName("최근 검색어를 정규화 키워드 기준으로 신규 저장한다")
    void recordRecentSearch_createsNewNormalizedKeyword() {
        when(recentSearchWriteService.updateRecentSearch(any(), any(), any(), any())).thenReturn(0);

        recentSearchCommandService.recordRecentSearch(1L, "  Test KEYWORD  ");

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> normalizedKeywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(recentSearchWriteService).createRecentSearch(
                userIdCaptor.capture(),
                keywordCaptor.capture(),
                normalizedKeywordCaptor.capture(),
                any());
        assertThat(userIdCaptor.getValue()).isEqualTo(1L);
        assertThat(keywordCaptor.getValue()).isEqualTo("Test KEYWORD");
        assertThat(normalizedKeywordCaptor.getValue()).isEqualTo("test keyword");
    }

    @Test
    @DisplayName("recent search keyword is capped at 255 characters")
    void recordRecentSearch_truncatesKeyword() {
        when(recentSearchWriteService.updateRecentSearch(any(), any(), any(), any())).thenReturn(0);
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);

        recentSearchCommandService.recordRecentSearch(1L, keyword);

        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> normalizedKeywordCaptor = ArgumentCaptor.forClass(String.class);
        verify(recentSearchWriteService).createRecentSearch(
                eq(1L),
                keywordCaptor.capture(),
                normalizedKeywordCaptor.capture(),
                any());
        assertThat(keywordCaptor.getValue()).hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH);
        assertThat(normalizedKeywordCaptor.getValue())
                .hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH)
                .isEqualTo("a".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
    }

    @Test
    @DisplayName("빈 검색어는 무시한다")
    void recordRecentSearch_ignoresBlankKeyword() {
        recentSearchCommandService.recordRecentSearch(1L, "   ");

        verify(searchUserLookupPolicy, never()).validateExists(any());
        verifyNoInteractions(recentSearchWriteService);
    }

    @Test
    @DisplayName("사용자가 없으면 예외를 던진다")
    void recordRecentSearch_throwsWhenUserMissing() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .when(searchUserLookupPolicy)
                .validateExists(1L);

        assertThatThrownBy(() -> recentSearchCommandService.recordRecentSearch(1L, "keyword"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 정규화 키워드가 있으면 최근 검색어를 갱신한다")
    void recordRecentSearch_updatesExistingNormalizedKeyword() {
        when(recentSearchWriteService.updateRecentSearch(any(), any(), any(), any())).thenReturn(1);

        recentSearchCommandService.recordRecentSearch(1L, "keyword");

        verify(recentSearchWriteService).updateRecentSearch(eq(1L), eq("keyword"), eq("keyword"), any());
        verify(recentSearchWriteService, never()).createRecentSearch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("삽입 충돌이 발생하면 재조회 없이 갱신으로 복구한다")
    void recordRecentSearch_recoversFromDuplicateInsert() {
        when(recentSearchWriteService.updateRecentSearch(any(), any(), any(), any()))
                .thenReturn(0)
                .thenReturn(1);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(recentSearchWriteService)
                .createRecentSearch(any(), any(), any(), any());

        recentSearchCommandService.recordRecentSearch(1L, "keyword");

        verify(recentSearchWriteService).createRecentSearch(eq(1L), eq("keyword"), eq("keyword"), any());
        verify(recentSearchWriteService, times(2)).updateRecentSearch(eq(1L), eq("keyword"), eq("keyword"), any());
    }
}
