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
import com.weedrice.whiteboard.domain.post.service.PostContentSummaryExtractor;
import com.weedrice.whiteboard.domain.post.service.PostInteractionContextResolver;
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
    private SearchRecordEventPublisher searchRecordEventPublisher;
    @Mock
    private SearchUserLookupPolicy searchUserLookupPolicy;
    private BoardAccessPolicy boardAccessPolicy;

    private SearchService searchService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testuser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        PostSummaryAssembler postSummaryAssembler = new PostSummaryAssembler(
                fileService,
                commentRepository,
                boardAccessPolicy,
                new PostInteractionContextResolver(
                        userRepository,
                        postLikeRepository,
                        scrapRepository,
                        boardSubscriptionRepository),
                new PostContentSummaryExtractor());
        searchService = new SearchService(
                searchStatisticRepository,
                searchStatisticCommandService,
                recentSearchCommandService,
                searchPersonalizationRepository,
                postRepository,
                boardRepository,
                userBlockService,
                postSummaryAssembler,
                boardAccessPolicy,
                searchRecordEventPublisher,
                searchUserLookupPolicy);
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
    @DisplayName("search record keyword is capped before statistics and recent search")
    void recordSearch_truncatesKeyword() {
        Long userId = 1L;
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);
        String canonicalKeyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH);

        searchService.recordSearch(userId, keyword, LocalDate.now());

        verify(searchStatisticCommandService).recordSearchStatistic(eq(canonicalKeyword), any(LocalDate.class));
        verify(recentSearchCommandService).recordRecentSearch(userId, canonicalKeyword);
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
    @DisplayName("게시글 검색은 createdAt 내림차순 정렬일 때 rowNum을 역순으로 부여한다")
    void searchPosts_assignsDescendingRowNumbers() {
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt")));
        Pageable normalizedPageable = PageRequest.of(1, 2, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
        var firstPost = post(11L, "first", LocalDateTime.of(2026, 4, 20, 10, 0));
        var secondPost = post(10L, "second", LocalDateTime.of(2026, 4, 20, 9, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), normalizedPageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(11L, 10L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchPostsWithPageable(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("게시글 검색은 createdAt 오름차순 정렬일 때 rowNum을 정순으로 부여한다")
    void searchPosts_assignsAscendingRowNumbersForCreatedAtSort() {
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Order.asc("createdAt")));
        Pageable normalizedPageable = PageRequest.of(1, 2, Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.desc("postId")));
        var firstPost = post(10L, "first", LocalDateTime.of(2026, 4, 20, 9, 0));
        var secondPost = post(11L, "second", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), normalizedPageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(10L, 11L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchPostsWithPageable(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("게시글 검색은 postId 오름차순 정렬일 때 rowNum을 정순으로 부여한다")
    void searchPosts_assignsAscendingRowNumbersForPostIdSort() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.asc("postId")));
        Pageable normalizedPageable = PageRequest.of(0, 2, Sort.by(
                Sort.Order.asc("postId"),
                Sort.Order.desc("createdAt")));
        var firstPost = post(10L, "first", LocalDateTime.of(2026, 4, 20, 9, 0));
        var secondPost = post(11L, "second", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), normalizedPageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(10L, 11L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchPostsWithPageable(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("게시글 검색은 createdAt 오름차순이 포함된 다중 정렬에서도 rowNum을 정순으로 부여한다")
    void searchPosts_assignsAscendingRowNumbersWhenCreatedAtAscendingAppearsInMultiSort() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("title"), Sort.Order.asc("createdAt")));
        Pageable normalizedPageable = PageRequest.of(0, 2, Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.desc("postId")));
        var firstPost = post(10L, "zeta", LocalDateTime.of(2026, 4, 20, 9, 0));
        var secondPost = post(11L, "alpha", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), normalizedPageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(10L, 11L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchPostsWithPageable(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("게시글 검색은 createdAt DESC, postId ASC 정렬에서 첫 정렬 기준대로 rowNum을 역순으로 부여한다")
    void searchPosts_assignsDescendingRowNumbersForMixedCreatedAtDescPostIdAscSort() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.asc("postId")));
        var firstPost = post(11L, "first", LocalDateTime.of(2026, 4, 20, 10, 0));
        var secondPost = post(10L, "second", LocalDateTime.of(2026, 4, 20, 10, 0));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), pageable, 5));
        when(fileService.getFirstImageFileIdsForPosts(List.of(11L, 10L)))
                .thenReturn(Collections.emptyMap());

        Page<PostSummary> result = searchPostsWithPageable(
                "test", null, null, pageable, null);

        assertThat(result.getContent()).extracting("rowNum").containsExactly(5L, 4L);
    }

    @Test
    @DisplayName("게시글 검색은 서비스 계층에서도 페이지 크기와 정렬 필드를 정규화한다")
    void searchPosts_normalizesPageableBeforeRepositoryCall() {
        Pageable pageable = PageRequest.of(3, 1000, Sort.by(Sort.Order.asc("unknown")));
        Pageable normalizedPageable = PageRequest.of(3, 100, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));

        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), eq(normalizedPageable)))
                .thenReturn(Page.empty(normalizedPageable));

        searchPostsWithPageable("test", null, null, pageable, null);

        verify(postRepository).searchPosts(
                eq("test"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                eq(normalizedPageable));
    }

    @Test
    @DisplayName("Post search normalizes boardUrl before lookup and repository call")
    void searchPosts_normalizesBoardUrlBeforeRepositoryCall() {
        Board board = board(1L, "Free", "free");
        Pageable pageable = PageRequest.of(0, 20);
        Pageable normalizedPageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.searchPosts(eq("test"), isNull(), eq("free"), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                eq(normalizedPageable)))
                .thenReturn(Page.empty(normalizedPageable));

        searchPostsWithPageable("test", null, " free ", pageable, null);

        verify(boardRepository).findByBoardUrl("free");
        verify(postRepository).searchPosts(
                eq("test"),
                isNull(),
                eq("free"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(false),
                isNull(),
                eq(normalizedPageable));
    }

    @Test
    @DisplayName("게시글 검색은 서비스 계층에서 blank 검색어를 거부한다")
    void searchPosts_rejectsBlankKeywordBeforeRepositoryCall() {
        assertThatThrownBy(() -> searchPostsWithPageable("   ", null, null, PageRequest.of(0, 20), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postRepository, never()).searchPosts(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색은 양방향 차단 사용자 목록을 전달한다")
    void searchPosts_authenticated_usesEitherDirectionBlockedUserFiltering() {
        Pageable pageable = PageRequest.of(0, 20);
        List<Long> blockedUserIds = List.of(2L, 3L);

        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(blockedUserIds);
        when(postRepository.searchPosts(eq("test"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(blockedUserIds), eq(false), eq(1L),
                any(Pageable.class))).thenReturn(Page.empty(pageable));

        searchPostsWithPageable("test", null, null, pageable, 1L);

        verify(userBlockService).getBlockedUserIdsEitherDirection(1L);
        verify(postRepository).searchPosts(eq("test"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(blockedUserIds), eq(false), eq(1L),
                any(Pageable.class));
        verify(searchRecordEventPublisher).publish(1L, "test");
    }

    @Test
    @DisplayName("게시글 검색 - 비공개 노드 접근 불가 시 BOARD_NOT_FOUND")
    void searchPosts_privateBoardDenied_throwsBoardNotFound() {
        Board privateBoard = board(2L, "Private", "private");
        User owner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 99L);
        ReflectionTestUtils.setField(privateBoard, "creator", owner);
        ReflectionTestUtils.setField(privateBoard, "isPublic", false);

        when(boardRepository.findByBoardUrl("private")).thenReturn(Optional.of(privateBoard));
        when(searchUserLookupPolicy.resolveOptional(1L)).thenReturn(user);
        when(adminRepository.existsByUserAndBoardAndIsActive(user, privateBoard, true)).thenReturn(false);

        assertThatThrownBy(() -> searchPostsWithPageable("test", null, "private", PageRequest.of(0, 20), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
        verify(postRepository, never()).searchPosts(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색 - 비활성 노드 접근 불가 시 BOARD_NOT_FOUND")
    void searchPosts_inactiveBoardDenied_throwsBoardNotFound() {
        Board inactiveBoard = board(2L, "Inactive", "inactive");
        User owner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 99L);
        ReflectionTestUtils.setField(inactiveBoard, "creator", owner);
        ReflectionTestUtils.setField(inactiveBoard, "isActive", false);

        when(boardRepository.findByBoardUrl("inactive")).thenReturn(Optional.of(inactiveBoard));
        when(searchUserLookupPolicy.resolveOptional(1L)).thenReturn(user);
        when(adminRepository.existsByUserAndBoardAndIsActive(user, inactiveBoard, true)).thenReturn(false);

        assertThatThrownBy(() -> searchPostsWithPageable("test", null, "inactive", PageRequest.of(0, 20), 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
        verify(postRepository, never()).searchPosts(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색 - 노드 관리자는 secret 포함 검색")
    void searchPosts_boardAdmin_includesSecretPosts() {
        Board privateBoard = board(2L, "Private", "private");
        User owner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 99L);
        ReflectionTestUtils.setField(privateBoard, "creator", owner);
        ReflectionTestUtils.setField(privateBoard, "isPublic", false);
        Pageable pageable = PageRequest.of(0, 20);

        when(boardRepository.findByBoardUrl("private")).thenReturn(Optional.of(privateBoard));
        when(searchUserLookupPolicy.resolveOptional(1L)).thenReturn(user);
        when(adminRepository.existsByUserAndBoardAndIsActive(user, privateBoard, true)).thenReturn(true);
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.searchPosts(eq("test"), isNull(), eq("private"), isNull(), isNull(), isNull(), eq(Collections.emptyList()),
                eq(true), eq(1L), any(Pageable.class))).thenReturn(Page.empty(pageable));

        searchPostsWithPageable("test", null, "private", pageable, 1L);

        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(1L);
        verify(userBlockService, never()).getBlockedUserIdsEitherDirection(1L);
        verify(postRepository).searchPosts(eq("test"), isNull(), eq("private"), isNull(), isNull(), isNull(), eq(Collections.emptyList()),
                eq(true), eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("게시글 검색 - 노드 생성자는 관리자 권한 없으면 비공개 노드 검색 불가")
    void searchPosts_boardCreator_deniedWithoutBoardAdminRole() {
        Board privateBoard = board(2L, "Private", "private");
        ReflectionTestUtils.setField(privateBoard, "isPublic", false);
        Pageable pageable = PageRequest.of(0, 20);

        when(boardRepository.findByBoardUrl("private")).thenReturn(Optional.of(privateBoard));
        when(searchUserLookupPolicy.resolveOptional(1L)).thenReturn(user);
        when(adminRepository.existsByUserAndBoardAndIsActive(user, privateBoard, true)).thenReturn(false);

        assertThatThrownBy(() -> searchPostsWithPageable("test", null, "private", pageable, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(postRepository, never()).searchPosts(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색 - 슈퍼 관리자는 secret 포함 검색")
    void searchPosts_superAdmin_includesSecretPosts() {
        Board privateBoard = board(2L, "Private", "private");
        User owner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 99L);
        ReflectionTestUtils.setField(privateBoard, "creator", owner);
        ReflectionTestUtils.setField(privateBoard, "isPublic", false);
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);
        Pageable pageable = PageRequest.of(0, 20);

        when(boardRepository.findByBoardUrl("private")).thenReturn(Optional.of(privateBoard));
        when(searchUserLookupPolicy.resolveOptional(1L)).thenReturn(user);
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.searchPosts(eq("test"), isNull(), eq("private"), isNull(), isNull(), isNull(), eq(Collections.emptyList()),
                eq(true), eq(1L), any(Pageable.class))).thenReturn(Page.empty(pageable));

        searchPostsWithPageable("test", null, "private", pageable, 1L);

        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(1L);
        verify(userBlockService, never()).getBlockedUserIdsEitherDirection(1L);
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(any(), any(), anyBoolean());
        verify(postRepository).searchPosts(eq("test"), isNull(), eq("private"), isNull(), isNull(), isNull(), eq(Collections.emptyList()),
                eq(true), eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("인기 검색어 조회 성공")
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
        assertThat(startDateCaptor.getValue().plusDays(6)).isEqualTo(endDateCaptor.getValue());
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
        Pageable normalizedPageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.desc("searchedAt"),
                Sort.Order.desc("logId")));
        SearchPersonalization personalization = SearchPersonalization.builder()
                .user(user)
                .keyword("test")
                .normalizedKeyword("test")
                .searchedAt(LocalDateTime.of(2026, 4, 22, 9, 0))
                .build();
        Page<SearchPersonalization> page = new PageImpl<>(List.of(personalization), normalizedPageable, 1);

        when(searchPersonalizationRepository.findRecentSearchesByUserId(eq(userId), eq(normalizedPageable)))
                .thenReturn(page);

        // when
        SearchPersonalizationResponse response = searchService.getRecentSearches(userId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(searchUserLookupPolicy, never()).validateExists(userId);
    }

    @Test
    @DisplayName("최근 검색어 조회 - pageable 정규화")
    void getRecentSearches_normalizesPageable() {
        Long userId = 1L;
        Pageable requestedPageable = PageRequest.of(3, 1000, Sort.by(
                Sort.Order.asc("searchedAt"),
                Sort.Order.asc("logId")));
        Pageable normalizedPageable = PageRequest.of(3, 100, Sort.by(
                Sort.Order.desc("searchedAt"),
                Sort.Order.desc("logId")));

        when(searchPersonalizationRepository.findRecentSearchesByUserId(eq(userId), eq(normalizedPageable)))
                .thenReturn(Page.empty(normalizedPageable));

        searchService.getRecentSearches(userId, requestedPageable);

        verify(searchPersonalizationRepository).findRecentSearchesByUserId(userId, normalizedPageable);
        verify(searchUserLookupPolicy).validateExists(userId);
    }

    @Test
    @DisplayName("최근 검색어 조회 - 결과가 있으면 사용자 존재 검증을 생략한다")
    void getRecentSearches_skipsUserLookupWhenPageHasContent() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Pageable normalizedPageable = PageRequest.of(0, 10, Sort.by(
                Sort.Order.desc("searchedAt"),
                Sort.Order.desc("logId")));
        SearchPersonalization personalization = SearchPersonalization.builder()
                .user(user)
                .keyword("test")
                .normalizedKeyword("test")
                .searchedAt(LocalDateTime.of(2026, 4, 22, 9, 0))
                .build();
        Page<SearchPersonalization> page = new PageImpl<>(List.of(personalization), normalizedPageable, 1);

        when(searchPersonalizationRepository.findRecentSearchesByUserId(userId, normalizedPageable))
                .thenReturn(page);

        searchService.getRecentSearches(userId, pageable);

        verify(searchUserLookupPolicy, never()).validateExists(userId);
    }

    @Test
    @DisplayName("최근 검색어 삭제 성공")
    void deleteRecentSearch_success() {
        // given
        Long userId = 1L;
        Long logId = 1L;

        when(searchPersonalizationRepository.deleteByLogIdAndUserId(logId, userId)).thenReturn(1);

        // when
        searchService.deleteRecentSearch(userId, logId);

        // then
        verify(searchPersonalizationRepository).deleteByLogIdAndUserId(logId, userId);
        verify(searchPersonalizationRepository, never()).findById(anyLong());
        verify(searchUserLookupPolicy, never()).validateExists(userId);
    }

    @Test
    @DisplayName("최근 검색어 삭제 - 없거나 소유하지 않은 logId는 NOT_FOUND")
    void deleteRecentSearch_missingOrNotOwned_throwsNotFound() {
        Long userId = 1L;
        Long logId = 99L;

        when(searchPersonalizationRepository.deleteByLogIdAndUserId(logId, userId)).thenReturn(0);

        assertThatThrownBy(() -> searchService.deleteRecentSearch(userId, logId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        verify(searchPersonalizationRepository).deleteByLogIdAndUserId(logId, userId);
        verify(searchPersonalizationRepository, never()).findById(anyLong());
        verify(searchUserLookupPolicy).validateExists(userId);
    }

    @Test
    @DisplayName("모든 최근 검색어 삭제 성공")
    void deleteAllRecentSearches_success() {
        // given
        Long userId = 1L;
        when(searchPersonalizationRepository.deleteAllByUserId(userId)).thenReturn(3);

        // when
        searchService.deleteAllRecentSearches(userId);

        // then
        verify(searchPersonalizationRepository).deleteAllByUserId(userId);
        verify(searchUserLookupPolicy, never()).validateExists(userId);
    }

    @Test
    @DisplayName("모든 최근 검색어 삭제 - 삭제 행이 없으면 사용자 존재를 확인한다")
    void deleteAllRecentSearches_validatesUserWhenNothingDeleted() {
        Long userId = 1L;
        when(searchPersonalizationRepository.deleteAllByUserId(userId)).thenReturn(0);

        searchService.deleteAllRecentSearches(userId);

        verify(searchPersonalizationRepository).deleteAllByUserId(userId);
        verify(searchUserLookupPolicy).validateExists(userId);
    }

    @Test
    void searchPosts_weekUsesSevenCalendarDays() {
        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        searchService.searchPosts("test", null, null, null, null, null, "WEEK",
                0, 20, Sort.unsorted(), null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepository).searchPosts(eq("test"), isNull(), isNull(), isNull(), fromCaptor.capture(),
                toCaptor.capture(), isNull(), eq(false), isNull(), any(Pageable.class));
        assertThat(java.time.Duration.between(fromCaptor.getValue(), toCaptor.getValue()).toDays()).isEqualTo(7L);
    }

    @Test
    @DisplayName("게시글 검색은 알 수 없는 검색 범위를 거부한다")
    void searchPosts_rejectsUnknownSearchTypeBeforeRepositoryCall() {
        assertThatThrownBy(() -> searchPostsWithPageable("test", "TITEL", null, PageRequest.of(0, 20), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(postRepository, never()).searchPosts(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    void searchPosts_monthUsesOneCalendarMonth() {
        when(postRepository.searchPosts(anyString(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(),
                any(Pageable.class))).thenReturn(Page.empty());

        searchService.searchPosts("test", null, null, null, null, null, "MONTH",
                0, 20, Sort.unsorted(), null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepository).searchPosts(eq("test"), isNull(), isNull(), isNull(), fromCaptor.capture(),
                toCaptor.capture(), isNull(), eq(false), isNull(), any(Pageable.class));
        assertThat(fromCaptor.getValue().toLocalDate())
                .isEqualTo(toCaptor.getValue().toLocalDate().minusMonths(1).plusDays(1));
    }

    @Test
    void searchPosts_rejectsReversedCustomDateRange() {
        assertThatThrownBy(() -> searchService.searchPosts(
                "test", null, null, null, "2026-07-20", "2026-07-10", "CUSTOM",
                0, 20, Sort.unsorted(), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(postRepository, never()).searchPosts(any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                any(), any());
    }

    @Test
    void popularKeywords_weeklyUsesSevenCalendarDays() {
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of());

        searchService.getPopularKeywords("WEEKLY", 10);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(searchStatisticRepository).findPopularKeywords(
                startCaptor.capture(), endCaptor.capture(), eq(PageRequest.of(0, 10)));
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(startCaptor.getValue(), endCaptor.getValue()))
                .isEqualTo(6L);
    }

    @Test
    void popularKeywords_monthlyMatchesPostSearchBoundary() {
        when(searchStatisticRepository.findPopularKeywords(any(), any(), any())).thenReturn(List.of());

        searchService.getPopularKeywords("MONTHLY", 10);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(searchStatisticRepository).findPopularKeywords(
                startCaptor.capture(), endCaptor.capture(), eq(PageRequest.of(0, 10)));
        assertThat(startCaptor.getValue()).isEqualTo(endCaptor.getValue().minusMonths(1).plusDays(1));
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

    private Page<PostSummary> searchPostsWithPageable(String keyword, String searchType, String boardUrl,
            Pageable pageable, Long currentUserId) {
        return searchService.searchPosts(
                keyword,
                searchType,
                boardUrl,
                null,
                null,
                null,
                null,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort(),
                currentUserId);
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
