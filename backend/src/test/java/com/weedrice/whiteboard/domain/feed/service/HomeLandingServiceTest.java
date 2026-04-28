package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeLandingServiceTest {

    @InjectMocks
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

    @Test
    @DisplayName("Landing response is split into featured, picks, trending, and live sections")
    void getLanding_slicesCuratedSections() {
        CustomUserDetails userDetails = new CustomUserDetails(
                1L,
                "test@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        List<PostSummary> curatedPosts = List.of(
                post(1L, "featured"),
                post(2L, "pick-1"),
                post(3L, "pick-2"),
                post(4L, "pick-3"),
                post(5L, "trend-1"),
                post(6L, "trend-2"));
        List<BoardListResponse> boards = List.of(
                board(1L, "free"),
                board(2L, "tech"));

        when(postService.getTrendingPosts(any(), eq(1L), eq("24h"))).thenReturn(curatedPosts);
        when(boardService.getTopBoards(userDetails)).thenReturn(boards);
        when(postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(any(), any()))
                .thenReturn(12L, 10L);
        when(postRepository.countVisiblePostsForAdminDashboard()).thenReturn(8421L);
        when(boardRepository.countByIsActiveTrueAndIsPublicTrue()).thenReturn(11L);
        when(userRepository.countByStatusAndDeletedAtIsNullAndCreatedAtAfter(eq(User.STATUS_ACTIVE), any()))
                .thenReturn(47L);
        when(userRepository.countRecentlyLoggedInActiveUsersForAdminDashboard(any()))
                .thenReturn(1247L);
        when(commentRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(any(), any()))
                .thenReturn(1824L);

        HomeLandingResponse response = homeLandingService.getLanding(userDetails, "24h");

        assertThat(response.getFeaturedPost().getPostId()).isEqualTo(1L);
        assertThat(response.getEditorPicks()).extracting(PostSummary::getPostId).containsExactly(2L, 3L, 4L);
        assertThat(response.getTrendingPosts()).extracting(PostSummary::getPostId).containsExactly(5L, 6L);
        assertThat(response.getLiveActivity()).extracting(PostSummary::getPostId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(response.getStats().getBoardCount()).isEqualTo(11L);
        assertThat(response.getStats().getPostCount()).isEqualTo(8421L);
        assertThat(response.getStats().getLiveCount()).isEqualTo(1824L);
        assertThat(response.getStats().getOnlineCount()).isEqualTo(1247L);
        assertThat(response.getStats().getPostsToday()).isEqualTo(12L);
        assertThat(response.getStats().getPostsTodayDeltaPercent()).isEqualTo(20);
        assertThat(response.getStats().getActiveBoardCount()).isEqualTo(11L);
        assertThat(response.getStats().getNewMembersLast24Hours()).isEqualTo(47L);
        assertThat(response.getStats().getCommentsToday()).isEqualTo(1824L);
    }

    @Test
    @DisplayName("Landing lookup propagates curated post failures")
    void getLanding_propagatesCuratedPostFailures() {
        when(postService.getTrendingPosts(any(), eq(null), eq("24h")))
                .thenThrow(new IllegalStateException("post failure"));

        assertThatThrownBy(() -> homeLandingService.getLanding(null, "24h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("post failure");

        verify(boardService, never()).getTopBoards(any());
        verify(postRepository, never()).countVisiblePostsForAdminDashboard();
    }

    @Test
    @DisplayName("Landing lookup propagates stats failures")
    void getLanding_propagatesStatsFailures() {
        when(postService.getTrendingPosts(any(), eq(null), eq("24h"))).thenReturn(List.of());
        when(boardService.getTopBoards(null)).thenReturn(List.of(board(1L, "free")));
        when(postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(any(), any()))
                .thenThrow(new IllegalStateException("stats failure"));

        assertThatThrownBy(() -> homeLandingService.getLanding(null, "24h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stats failure");
    }

    @Test
    @DisplayName("Landing lookup propagates board failures")
    void getLanding_propagatesBoardFailures() {
        when(postService.getTrendingPosts(any(), eq(null), eq("24h"))).thenReturn(List.of());
        when(boardService.getTopBoards(null)).thenThrow(new IllegalStateException("board failure"));

        assertThatThrownBy(() -> homeLandingService.getLanding(null, "24h"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("board failure");

        verify(postRepository, never()).countVisiblePostsForAdminDashboard();
    }

    private PostSummary post(Long postId, String title) {
        return PostSummary.builder()
                .postId(postId)
                .author(PostSummary.AuthorInfo.builder()
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
                .createdAt(java.time.LocalDateTime.now())
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
