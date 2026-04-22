package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

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
    private UserRepository userRepository;

    @InjectMocks
    private RecentSearchCommandService recentSearchCommandService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("test-user").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Test
    @DisplayName("최근 검색어를 정규화 키워드 기준으로 신규 저장한다")
    void recordRecentSearch_createsNewNormalizedKeyword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
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
    @DisplayName("정규화 키워드는 trim과 Locale.ROOT 소문자 규칙을 따른다")
    void normalizeKeyword_trimsAndLowercases() {
        assertThat(RecentSearchCommandService.normalizeKeyword("\t Test KEYWORD \n"))
                .isEqualTo("test keyword");
    }

    @Test
    @DisplayName("빈 검색어는 무시한다")
    void recordRecentSearch_ignoresBlankKeyword() {
        recentSearchCommandService.recordRecentSearch(1L, "   ");

        verify(userRepository, never()).findById(any());
        verifyNoInteractions(recentSearchWriteService);
    }

    @Test
    @DisplayName("사용자가 없으면 예외를 던진다")
    void recordRecentSearch_throwsWhenUserMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recentSearchCommandService.recordRecentSearch(1L, "keyword"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("기존 정규화 키워드가 있으면 최근 검색어를 갱신한다")
    void recordRecentSearch_updatesExistingNormalizedKeyword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recentSearchWriteService.updateRecentSearch(any(), any(), any(), any())).thenReturn(1);

        recentSearchCommandService.recordRecentSearch(1L, "keyword");

        verify(recentSearchWriteService).updateRecentSearch(eq(1L), eq("keyword"), eq("keyword"), any());
        verify(recentSearchWriteService, never()).createRecentSearch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("삽입 충돌이 발생하면 재조회 없이 갱신으로 복구한다")
    void recordRecentSearch_recoversFromDuplicateInsert() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
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
