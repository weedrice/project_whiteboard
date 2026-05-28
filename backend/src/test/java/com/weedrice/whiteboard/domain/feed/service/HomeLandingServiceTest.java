package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeLandingServiceTest {

    private HomeLandingService homeLandingService;

    @Mock
    private PostService postService;

    @Mock
    private BoardService boardService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-04-28T16:05:00Z"), DateTimeUtils.KST_ZONE_ID);
        homeLandingService = new HomeLandingService(
                postService,
                boardService,
                postRepository,
                boardRepository,
                commentRepository,
                userRepository,
                clock,
                new HomeLandingSectionAssembler());
    }

    @Test
    @DisplayName("Landing response returns layout independent source posts")
    void getLanding_returnsCuratedPosts() {
        List<FeedPostSummary> curatedPosts = List.of(
                post(1L, "featured"),
                post(2L, "pick-1"),
                post(3L, "pick-2"),
                post(4L, "pick-3"),
                post(5L, "trend-1"),
                post(6L, "trend-2"));
        List<FeedPostSummary> latestPosts = List.of(
                post(11L, "latest-1"),
                post(10L, "latest-2"));
        List<BoardListResponse> boards = List.of(
                board(1L, "free"),
                board(2L, "tech"));

        when(postService.getTrendingFeedPosts(any(), eq(1L), eq("24h"))).thenReturn(curatedPosts);
        when(postService.getPublicLandingLatestFeedPosts(any(), eq(1L))).thenReturn(latestPosts);
        when(boardService.getTopBoardsByUserId(1L, 7)).thenReturn(boards);
        when(postRepository.countPublicLandingPostStats(any(), any(), any(), eq(BoardPolicyConstants.INQUIRY_BOARD_URL)))
                .thenReturn(postStats(8421L, 12L, 10L));
        when(boardRepository.countPublicLandingVisibleBoards(BoardPolicyConstants.INQUIRY_BOARD_URL)).thenReturn(11L);
        when(userRepository.countPublicLandingUserStats(any()))
                .thenReturn(userStats(47L, 1247L));
        when(commentRepository.countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(),
                any(),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL)))
                .thenReturn(1824L);

        HomeLandingResponse response = homeLandingService.getLanding(1L, "24h");

        assertThat(response.getFeaturedPost().getPostId()).isEqualTo(1L);
        assertThat(response.getEditorPicks()).extracting(FeedPostSummary::getPostId)
                .containsExactly(2L, 3L, 4L);
        assertThat(response.getTrendingPosts()).extracting(FeedPostSummary::getPostId)
                .containsExactly(2L, 3L, 4L, 5L, 6L);
        assertThat(response.getLiveActivityPosts()).extracting(FeedPostSummary::getPostId)
                .containsExactly(11L, 10L);
        assertThat(response.getStats().getBoardCount()).isEqualTo(11L);
        assertThat(response.getStats().getPostCount()).isEqualTo(8421L);
        assertThat(response.getStats().getLiveCount()).isEqualTo(1824L);
        assertThat(response.getStats().getOnlineCount()).isEqualTo(1247L);
        assertThat(response.getStats().getPostsToday()).isEqualTo(12L);
        assertThat(response.getStats().getPostsTodayDeltaPercent()).isEqualTo(20);
        assertThat(response.getStats().getActiveBoardCount()).isEqualTo(11L);
        assertThat(response.getStats().getNewMembersLast24Hours()).isEqualTo(47L);
        assertThat(response.getStats().getCommentsToday()).isEqualTo(1824L);
        verify(boardService).getTopBoardsByUserId(1L, 7);
        verify(postService).getPublicLandingLatestFeedPosts(any(), eq(1L));
    }

    @Test
    @DisplayName("Landing lookup propagates curated post failures")
    void getLanding_propagatesCuratedPostFailures() {
        when(postService.getTrendingFeedPosts(any(), isNull(), eq("24h")))
                .thenThrow(new IllegalStateException("post failure"));

        assertThatThrownBy(() -> homeLandingService.getLanding(null, "24h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("post failure");

        verify(boardService, never()).getTopBoardsByUserId(any(), anyInt());
        verify(postService, never()).getPublicLandingLatestFeedPosts(any(), any());
        verify(postRepository, never()).countPublicLandingPostStats(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Landing lookup propagates stats failures")
    void getLanding_propagatesStatsFailures() {
        when(postService.getTrendingFeedPosts(any(), isNull(), eq("24h"))).thenReturn(List.of());
        when(postService.getPublicLandingLatestFeedPosts(any(), isNull())).thenReturn(List.of());
        when(boardService.getTopBoardsByUserId(isNull(), eq(7))).thenReturn(List.of(board(1L, "free")));
        when(postRepository.countPublicLandingPostStats(any(), any(), any(), eq(BoardPolicyConstants.INQUIRY_BOARD_URL)))
                .thenThrow(new IllegalStateException("stats failure"));

        assertThatThrownBy(() -> homeLandingService.getLanding(null, "24h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stats failure");
    }

    @Test
    @DisplayName("Landing lookup propagates board failures")
    void getLanding_propagatesBoardFailures() {
        when(postService.getTrendingFeedPosts(any(), isNull(), eq("24h"))).thenReturn(List.of());
        when(postService.getPublicLandingLatestFeedPosts(any(), isNull())).thenReturn(List.of());
        when(boardService.getTopBoardsByUserId(isNull(), eq(7))).thenThrow(new IllegalStateException("board failure"));

        assertThatThrownBy(() -> homeLandingService.getLanding(null, "24h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("board failure");

        verify(postRepository, never()).countPublicLandingPostStats(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Landing stats use KST day boundaries")
    void getLanding_statsUseKstDayBoundaries() {
        when(postService.getTrendingFeedPosts(any(), isNull(), eq("24h"))).thenReturn(List.of());
        when(postService.getPublicLandingLatestFeedPosts(any(), isNull())).thenReturn(List.of());
        when(boardService.getTopBoardsByUserId(isNull(), eq(7))).thenReturn(List.of());
        when(postRepository.countPublicLandingPostStats(any(), any(), any(), eq(BoardPolicyConstants.INQUIRY_BOARD_URL)))
                .thenReturn(postStats(8421L, 12L, 10L));
        when(boardRepository.countPublicLandingVisibleBoards(BoardPolicyConstants.INQUIRY_BOARD_URL)).thenReturn(11L);
        when(userRepository.countPublicLandingUserStats(any())).thenReturn(userStats(47L, 1247L));
        when(commentRepository.countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                any(),
                any(),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL)))
                .thenReturn(1824L);

        homeLandingService.getLanding(null, "24h");

        ArgumentCaptor<LocalDateTime> postStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> postEndCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> yesterdayStartCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepository).countPublicLandingPostStats(
                postStartCaptor.capture(),
                postEndCaptor.capture(),
                yesterdayStartCaptor.capture(),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL));
        assertThat(postStartCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 29, 0, 0));
        assertThat(postEndCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 30, 0, 0));
        assertThat(yesterdayStartCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 28, 0, 0));
        verify(userRepository).countPublicLandingUserStats(
                LocalDateTime.of(2026, 4, 28, 1, 5));
        verify(commentRepository).countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                LocalDateTime.of(2026, 4, 29, 0, 0),
                LocalDateTime.of(2026, 4, 30, 0, 0),
                BoardPolicyConstants.INQUIRY_BOARD_URL);
    }

    private PostRepository.PublicLandingPostStatsProjection postStats(
            long totalPosts,
            long postsToday,
            long postsYesterday) {
        return new PostRepository.PublicLandingPostStatsProjection() {
            @Override
            public Long getTotalPosts() {
                return totalPosts;
            }

            @Override
            public Long getPostsToday() {
                return postsToday;
            }

            @Override
            public Long getPostsYesterday() {
                return postsYesterday;
            }
        };
    }

    private UserRepository.PublicLandingUserStatsProjection userStats(long newMembersLast24Hours, long onlineCount) {
        return new UserRepository.PublicLandingUserStatsProjection() {
            @Override
            public Long getNewMembersLast24Hours() {
                return newMembersLast24Hours;
            }

            @Override
            public Long getOnlineCount() {
                return onlineCount;
            }
        };
    }

    private FeedPostSummary post(Long postId, String title) {
        return FeedPostSummary.builder()
                .postId(postId)
                .author(FeedPostSummary.AuthorInfo.builder()
                        .displayName("author")
                        .build())
                .title(title)
                .boardUrl("free")
                .boardName("Free")
                .viewCount(1)
                .likeCount(0)
                .commentCount(0)
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private BoardListResponse board(Long boardId, String boardUrl) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);
        ReflectionTestUtils.setField(board, "sortOrder", 1);
        return new BoardListResponse(board, 10L, 120L, "admin", false);
    }
}
