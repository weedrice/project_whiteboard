package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.post.service.PostContentSummaryExtractor;
import com.weedrice.whiteboard.domain.post.service.PostInteractionContextResolver;
import com.weedrice.whiteboard.domain.post.service.PostSummaryAssembler;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchPreviewReadServiceTest {

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

    private SearchPreviewReadService searchPreviewReadService;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testuser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        BoardAccessPolicy boardAccessPolicy = new BoardAccessPolicy(adminRepository);
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
        searchPreviewReadService = new SearchPreviewReadService(
                userRepository,
                postRepository,
                commentRepository,
                boardRepository,
                boardAccessPolicy,
                userBlockService,
                postSummaryAssembler,
                new IntegratedSearchAssembler(),
                new SearchUserLookupPolicy(userRepository));
    }

    @Test
    @DisplayName("통합 검색 preview - 비로그인 사용자는 공개 댓글 결과를 조회한다")
    void integratedSearch_anonymous_searchesPublicComments() {
        String keyword = "test";
        Pageable previewPageable = PageRequest.of(0, 5);

        givenEmptyPreview(keyword, previewPageable);

        var result = searchPreviewReadService.integratedSearch(keyword, null);

        assertThat(result.getCommentResults().getTotalElements()).isZero();
        assertThat(result.getBoardResults()).isEmpty();
        verify(commentRepository).searchCommentsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class));
        verify(boardRepository).findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), eq(previewPageable));
        verify(fileService, never()).getFirstImageFileIdsForPosts(anyList());
    }

    @Test
    @DisplayName("통합 검색 preview는 댓글 미리보기 정렬을 고정한다")
    void integratedSearch_usesStableCommentPreviewSort() {
        String keyword = "test";
        Pageable previewPageable = PageRequest.of(0, 5);
        givenEmptyPreview(keyword, previewPageable);

        searchPreviewReadService.integratedSearch(keyword, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).searchCommentsByKeyword(eq(keyword), isNull(), isNull(), pageableCaptor.capture());
        Pageable commentPageable = pageableCaptor.getValue();
        assertThat(commentPageable.getPageNumber()).isZero();
        assertThat(commentPageable.getPageSize()).isEqualTo(5);
        assertThat(commentPageable.getSort()).containsExactly(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("commentId"));
    }

    @Test
    @DisplayName("통합 검색 preview는 키워드를 trim 한다")
    void integratedSearch_trimsKeywordBeforeRepositoryCall() {
        String keyword = "test";
        Pageable previewPageable = PageRequest.of(0, 5);
        givenEmptyPreview(keyword, previewPageable);

        var result = searchPreviewReadService.integratedSearch("  test  ", null);

        assertThat(result.getKeyword()).isEqualTo(keyword);
        verify(postRepository).searchPostsByKeyword(eq(keyword), isNull(), isNull(), eq(previewPageable));
    }

    @Test
    @DisplayName("통합 검색 preview는 긴 키워드를 255자로 제한한다")
    void integratedSearch_truncatesKeywordBeforeRepositoryCall() {
        String rawKeyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);
        String canonicalKeyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH);
        Pageable previewPageable = PageRequest.of(0, 5);
        givenEmptyPreview(canonicalKeyword, previewPageable);

        var result = searchPreviewReadService.integratedSearch(rawKeyword, null);

        assertThat(result.getKeyword())
                .hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH)
                .isEqualTo(canonicalKeyword);
        verify(postRepository).searchPostsByKeyword(eq(canonicalKeyword), isNull(), isNull(), eq(previewPageable));
    }

    @Test
    @DisplayName("통합 검색 preview는 blank 키워드를 거부한다")
    void integratedSearch_rejectsBlankKeyword() {
        assertThatThrownBy(() -> searchPreviewReadService.integratedSearch("   ", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postRepository, never()).searchPostsByKeyword(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("통합 검색 preview - 로그인 사용자는 차단 사용자 필터링을 적용한다")
    void integratedSearch_authenticated_usesBlockedUserFiltering() {
        String keyword = "test";
        Long currentUserId = 1L;
        Pageable previewPageable = PageRequest.of(0, 5);
        List<Long> blockedUserIds = List.of(2L, 3L);

        when(userBlockService.getBlockedUserIdsEitherDirection(currentUserId)).thenReturn(blockedUserIds);
        when(postRepository.searchPostsByKeyword(eq(keyword), anyList(), eq(currentUserId), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(commentRepository.searchCommentsByKeyword(eq(keyword), eq(blockedUserIds), eq(currentUserId),
                any(Pageable.class))).thenReturn(Page.empty(previewPageable));
        when(userRepository.searchUsersVisibleTo(eq(keyword), eq(blockedUserIds), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(boardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        var result = searchPreviewReadService.integratedSearch(keyword, currentUserId);

        assertThat(result.getCommentResults().getTotalElements()).isZero();
        assertThat(result.getBoardResults()).isEmpty();
        verify(userBlockService).getBlockedUserIdsEitherDirection(currentUserId);
        verify(commentRepository).searchCommentsByKeyword(eq(keyword), eq(blockedUserIds), eq(currentUserId),
                any(Pageable.class));
        verify(boardRepository).findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), eq(previewPageable));
    }

    @Test
    @DisplayName("통합 검색 preview - 보드는 공개 활성 노드 5개만 조회한다")
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

        var result = searchPreviewReadService.integratedSearch(keyword, null);

        assertThat(result.getBoardResults()).hasSize(5);
        assertThat(result.getBoardResults()).extracting("boardName")
                .containsExactly("Board 1", "Board 2", "Board 3", "Board 4", "Board 5");
        verify(boardRepository).findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), eq(previewPageable));
    }

    @Test
    @DisplayName("필터 통합 검색은 boardUrl을 정규화하고 게시글과 댓글 범위에 전달한다")
    void integratedSearch_withBoardUrl_validatesAndScopesPostAndCommentSearch() {
        String keyword = "test";
        Board board = board(10L, "Free", "free");
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        when(postRepository.searchPosts(
                eq(keyword), isNull(), eq("free"), isNull(), isNull(), isNull(),
                eq(List.of()), eq(false), eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 5)));
        when(commentRepository.searchCommentsByKeyword(
                eq(keyword), eq("free"), eq(List.of()), eq(false), eq(1L), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 5)));
        when(userRepository.searchUsersVisibleTo(eq(keyword), eq(List.of()), any(Pageable.class)))
                .thenReturn(Page.empty(PageRequest.of(0, 5)));
        when(boardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), any(Pageable.class)))
                .thenReturn(List.of());

        searchPreviewReadService.integratedSearch(
                keyword, null, " free ", null, null, null, null, Sort.unsorted(), 1L);

        verify(postRepository).searchPosts(
                eq(keyword), isNull(), eq("free"), isNull(), isNull(), isNull(),
                eq(List.of()), eq(false), eq(1L), any(Pageable.class));
        verify(commentRepository).searchCommentsByKeyword(
                eq(keyword), eq("free"), eq(List.of()), eq(false), eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("필터 통합 검색은 존재하지 않는 boardUrl을 거부한다")
    void integratedSearch_withMissingBoard_throwsBoardNotFound() {
        when(boardRepository.findByBoardUrl("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchPreviewReadService.integratedSearch(
                "test", null, "missing", null, null, null, null, Sort.unsorted(), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(postRepository, never()).searchPosts(anyString(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(Pageable.class));
    }

    private void givenEmptyPreview(String keyword, Pageable previewPageable) {
        when(postRepository.searchPostsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(commentRepository.searchCommentsByKeyword(eq(keyword), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(userRepository.searchUsersVisibleTo(eq(keyword), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty(previewPageable));
        when(boardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                eq(keyword), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
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

}
