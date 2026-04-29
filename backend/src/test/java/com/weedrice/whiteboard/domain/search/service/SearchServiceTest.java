package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.entity.SearchPersonalization;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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
    private SearchStatisticCommandService searchStatisticCommandService;
    @Mock
    private RecentSearchCommandService recentSearchCommandService;
    @Mock
    private SearchPersonalizationRepository searchPersonalizationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private FileService fileService;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private ScrapRepository scrapRepository;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock
    private BoardAccessPolicy boardAccessPolicy;

    private SearchService searchService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testuser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(
                fileService,
                userRepository,
                postLikeRepository,
                scrapRepository,
                boardSubscriptionRepository,
                commentRepository,
                boardAccessPolicy);
        searchService = new SearchService(
                searchStatisticRepository,
                searchStatisticCommandService,
                recentSearchCommandService,
                searchPersonalizationRepository,
                userRepository,
                postRepository,
                commentRepository,
                boardRepository,
                adminRepository,
                userBlockService,
                postSummaryAssembler);
    }

    @Test
    @DisplayName("검색 기록 저장 성공")
    void recordSearch_success() {
        // given
        Long userId = 1L;
        String keyword = "test";

        // when
        searchService.recordSearch(userId, keyword, LocalDate.now());

        // then
        verify(searchStatisticCommandService).recordSearchStatistic(eq(keyword), any(LocalDate.class));
        verify(recentSearchCommandService).recordRecentSearch(userId, keyword);
    }

    @Test
    @DisplayName("검색 기록 저장 성공 - userId가 null인 경우")
    void recordSearch_success_withNullUserId() {
        // given
        String keyword = "test";
        // when
        searchService.recordSearch(null, keyword, LocalDate.now());

        // then
        verify(searchStatisticCommandService).recordSearchStatistic(eq(keyword), any(LocalDate.class));
        verify(recentSearchCommandService, never()).recordRecentSearch(any(), any());
    }

    @Test
    @DisplayName("최근 검색어 저장이 실패해도 검색 통계 적재는 유지한다")
    void recordSearch_keepsStatisticsWhenRecentSearchFails() {
        Long userId = 1L;
        String keyword = "test";
        doThrow(new RuntimeException("recent search failed"))
                .when(recentSearchCommandService)
                .recordRecentSearch(userId, keyword);

        searchService.recordSearch(userId, keyword, LocalDate.now());

        verify(searchStatisticCommandService).recordSearchStatistic(eq(keyword), any(LocalDate.class));
        verify(recentSearchCommandService).recordRecentSearch(userId, keyword);
    }

    @Test
    @DisplayName("통합 검색 - 비로그인 사용자는 댓글 결과가 비어있음")
    void integratedSearch_anonymous_searchesPublicComments() {
        String keyword = "test";
        Pageable previewPageable = PageRequest.of(0, 5);

        when(postRepository.searchPostsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(commentRepository.searchCommentsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(userRepository.searchUsersVisibleTo(eq(keyword), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(boardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        var result = searchService.integratedSearch(keyword, null);

        assertThat(result.getComments().getTotalElements()).isZero();
        assertThat(result.getBoards()).isEmpty();
        verify(commentRepository).searchCommentsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class));
        verify(boardRepository).findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), eq(previewPageable));
        verify(fileService, never()).getFirstImageFileIdsForPosts(anyList());
    }

    @Test
    @DisplayName("통합 검색 - 로그인 사용자는 본인 댓글만 조회")
    void integratedSearch_authenticated_usesBlockedUserFiltering() {
        String keyword = "test";
        Long currentUserId = 1L;
        Pageable previewPageable = PageRequest.of(0, 5);
        List<Long> blockedUserIds = List.of(2L, 3L);

        when(userBlockService.getBlockedUserIds(currentUserId)).thenReturn(blockedUserIds);
        when(postRepository.searchPostsByKeyword(eq(keyword), anyList(), eq(currentUserId), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(commentRepository.searchCommentsByKeyword(eq(keyword), eq(blockedUserIds), eq(currentUserId),
                any(Pageable.class))).thenReturn(Page.empty(previewPageable));
        when(userRepository.searchUsersVisibleTo(eq(keyword), eq(blockedUserIds), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(boardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        var result = searchService.integratedSearch(keyword, currentUserId);

        assertThat(result.getComments().getTotalElements()).isZero();
        assertThat(result.getBoards()).isEmpty();
        verify(commentRepository).searchCommentsByKeyword(eq(keyword), eq(blockedUserIds), eq(currentUserId),
                any(Pageable.class));
        verify(boardRepository).findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), eq(previewPageable));
    }

    @Test
    @DisplayName("통합 검색 - 보드 미리보기는 공개 활성 게시판 5개만 조회")
    void integratedSearch_limitsBoardPreviewAtRepositoryLevel() {
        String keyword = "test";
        Pageable previewPageable = PageRequest.of(0, 5);
        List<Board> boards = List.of(
                board(1L, "Board 1", "board1"),
                board(2L, "Board 2", "board2"),
                board(3L, "Board 3", "board3"),
                board(4L, "Board 4", "board4"),
                board(5L, "Board 5", "board5"));

        when(postRepository.searchPostsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(commentRepository.searchCommentsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(userRepository.searchUsersVisibleTo(eq(keyword), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(boardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), any(Pageable.class)))
                .thenReturn(boards);

        var result = searchService.integratedSearch(keyword, null);

        assertThat(result.getBoards()).hasSize(5);
        assertThat(result.getBoards()).extracting("boardName")
                .containsExactly("Board 1", "Board 2", "Board 3", "Board 4", "Board 5");
        verify(boardRepository).findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), eq(previewPageable));
    }

    @Test
    @DisplayName("게시글 검색은 createdAt 내림차순 정렬일 때 rowNum을 역순으로 부여한다")
    void searchPosts_assignsDescendingRowNumbers() {
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt")));
        var firstPost = post(11L, "first", LocalDateTime.of(2026, 4, 20, 10, 0));
        var secondPost = post(10L, "second", LocalDateTime.of(2026, 4, 20, 9, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), anyBoolean(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), pageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(11L, 10L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchService.searchPosts(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("게시글 검색은 createdAt 오름차순 정렬일 때 rowNum을 정순으로 부여한다")
    void searchPosts_assignsAscendingRowNumbersForCreatedAtSort() {
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.asc("createdAt")));
        var firstPost = post(10L, "first", LocalDateTime.of(2026, 4, 20, 9, 0));
        var secondPost = post(11L, "second", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), anyBoolean(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), pageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(10L, 11L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchService.searchPosts(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("게시글 검색은 postId 오름차순 정렬일 때 rowNum을 정순으로 부여한다")
    void searchPosts_assignsAscendingRowNumbersForPostIdSort() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.asc("postId")));
        var firstPost = post(10L, "first", LocalDateTime.of(2026, 4, 20, 9, 0));
        var secondPost = post(11L, "second", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), anyBoolean(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), pageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(10L, 11L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchService.searchPosts(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("게시글 검색은 createdAt 오름차순이 포함된 다중 정렬에서도 rowNum을 정순으로 부여한다")
    void searchPosts_assignsAscendingRowNumbersWhenCreatedAtAscendingAppearsInMultiSort() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("title"), Sort.Order.asc("createdAt")));
        Pageable normalizedPageable = PageRequest.of(0, 2, Sort.by(Sort.Order.asc("createdAt")));
        var firstPost = post(10L, "zeta", LocalDateTime.of(2026, 4, 20, 9, 0));
        var secondPost = post(11L, "alpha", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), normalizedPageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(10L, 11L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchService.searchPosts(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("게시글 검색은 서비스 계층에서도 페이지 크기와 정렬 필드를 정규화한다")
    void searchPosts_normalizesPageableBeforeRepositoryCall() {
        Pageable pageable = PageRequest.of(3, 1000, Sort.by(Sort.Order.asc("unknown")));
        Pageable normalizedPageable = PageRequest.of(3, 100, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(Page.empty(normalizedPageable));

        searchService.searchPosts("test", null, null, pageable, null);

        verify(postRepository).searchPosts(
                eq("test"),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                eq(normalizedPageable));
    }

    @Test
    @DisplayName("?멸린 寃?됱뼱 議고쉶 ?깃났")
    void getPopularKeywords_success() {
        // given
        SearchStatisticRepository.PopularKeywordProjection result1 = popularKeyword("keyword1", 10L);
        SearchStatisticRepository.PopularKeywordProjection result2 = popularKeyword("keyword2", 5L);
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of(result1, result2));

        // when
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords("WEEKLY", 10);

        // then
        assertThat(popularKeywords).hasSize(2);
        assertThat(popularKeywords.get(0).getKeyword()).isEqualTo("keyword1");
        assertThat(popularKeywords.get(0).getCount()).isEqualTo(10L);
        verify(searchStatisticRepository).findPopularKeywords(any(), any(), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("인기 검색어 조회 성공 - DAILY")
    void getPopularKeywords_success_daily() {
        // given
        SearchStatisticRepository.PopularKeywordProjection result1 = popularKeyword("keyword1", 10L);
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of(result1));

        // when
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords("DAILY", 10);

        // then
        assertThat(popularKeywords).hasSize(1);
    }

    @Test
    @DisplayName("인기 검색어 조회 성공 - MONTHLY")
    void getPopularKeywords_success_monthly() {
        // given
        SearchStatisticRepository.PopularKeywordProjection result1 = popularKeyword("keyword1", 10L);
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of(result1));

        // when
        List<PopularKeywordDto> popularKeywords = searchService.getPopularKeywords("MONTHLY", 10);

        // then
        assertThat(popularKeywords).hasSize(1);
    }

    @Test
    @DisplayName("인기 검색어 제한이 최대값을 넘으면 100개로 제한한다")
    void getPopularKeywords_clampsLimit() {
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of());

        searchService.getPopularKeywords("WEEKLY", 101);

        verify(searchStatisticRepository).findPopularKeywords(any(), any(), eq(PageRequest.of(0, 100)));
    }

    @Test
    @DisplayName("인기 검색어 기간은 대소문자와 공백을 정규화한다")
    void getPopularKeywords_normalizesPeriod() {
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of());

        searchService.getPopularKeywords(" weekly ", 10);

        ArgumentCaptor<LocalDate> startDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(searchStatisticRepository).findPopularKeywords(
                startDateCaptor.capture(),
                endDateCaptor.capture(),
                eq(PageRequest.of(0, 10)));
        assertThat(startDateCaptor.getValue().plusWeeks(1)).isEqualTo(endDateCaptor.getValue());
    }

    @Test
    @DisplayName("인기 검색어 기간이 없으면 DAILY를 기본값으로 사용한다")
    void getPopularKeywords_defaultsBlankPeriodToDaily() {
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of());

        searchService.getPopularKeywords(null, 10);
        searchService.getPopularKeywords("   ", 10);

        ArgumentCaptor<LocalDate> startDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(searchStatisticRepository, times(2)).findPopularKeywords(
                startDateCaptor.capture(),
                endDateCaptor.capture(),
                eq(PageRequest.of(0, 10)));
        assertThat(startDateCaptor.getAllValues()).containsExactlyElementsOf(endDateCaptor.getAllValues());
    }

    @Test
    @DisplayName("인기 검색어 제한이 0 이하면 검증 오류를 반환한다")
    void getPopularKeywords_rejectsLimitZeroOrLess() {
        assertThatThrownBy(() -> searchService.getPopularKeywords("WEEKLY", 0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(searchStatisticRepository, never()).findPopularKeywords(any(), any(), any());
    }

    @Test
    @DisplayName("인기 검색어 기간이 유효하지 않으면 검증 오류를 반환한다")
    void getPopularKeywords_rejectsInvalidPeriod() {
        assertThatThrownBy(() -> searchService.getPopularKeywords("YEARLY", 10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(searchStatisticRepository, never()).findPopularKeywords(any(), any(), any());
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
                .normalizedKeyword("test")
                .searchedAt(LocalDateTime.of(2026, 4, 22, 9, 0))
                .build();
        Page<SearchPersonalization> page = new PageImpl<>(List.of(personalization), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(searchPersonalizationRepository.findByUserOrderBySearchedAtDesc(eq(user), eq(pageable)))
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
                .normalizedKeyword("test")
                .searchedAt(LocalDateTime.of(2026, 4, 22, 9, 0))
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

    private SearchStatisticRepository.PopularKeywordProjection popularKeyword(String keyword, Long count) {
        return new SearchStatisticRepository.PopularKeywordProjection() {
            @Override
            public String getKeyword() {
                return keyword;
            }

            @Override
            public Long getCount() {
                return count;
            }
        };
    }

    private Board board(Long boardId, String boardName, String boardUrl) {
        Board board = Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(user)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "sortOrder", boardId.intValue());
        return board;
    }

    private Post post(Long postId, String title, LocalDateTime createdAt) {
        Post post = Post.builder()
                .board(board(1L, "Board", "board"))
                .user(user)
                .title(title)
                .contents("<p>contents</p>")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        ReflectionTestUtils.setField(post, "postId", postId);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        return post;
    }
}
