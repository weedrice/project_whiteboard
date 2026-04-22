package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeLandingServiceTest {

    @InjectMocks
    private HomeLandingService homeLandingService;

    @Mock
    private PostService postService;

    @Mock
    private BoardService boardService;

    @Test
    @DisplayName("홈 랜딩 응답을 featured, picks, trending, live 로 분배한다")
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

        when(postService.getTrendingPosts(any(), eq(1L))).thenReturn(curatedPosts);
        when(boardService.getTopBoards(userDetails)).thenReturn(boards);

        HomeLandingResponse response = homeLandingService.getLanding(userDetails);

        assertThat(response.getFeaturedPost().getPostId()).isEqualTo(1L);
        assertThat(response.getEditorPicks()).extracting(PostSummary::getPostId).containsExactly(2L, 3L, 4L);
        assertThat(response.getTrendingPosts()).extracting(PostSummary::getPostId).containsExactly(5L, 6L);
        assertThat(response.getLiveActivity()).extracting(PostSummary::getPostId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(response.getStats().getBoardCount()).isEqualTo(2);
        assertThat(response.getStats().getPostCount()).isEqualTo(6);
        assertThat(response.getStats().getLiveCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("일부 소스가 실패해도 홈 랜딩은 부분 응답을 반환한다")
    void getLanding_toleratesPartialFailures() {
        when(postService.getTrendingPosts(any(), eq(null))).thenThrow(new IllegalStateException("post failure"));
        when(boardService.getTopBoards(null)).thenReturn(List.of(board(1L, "free")));

        HomeLandingResponse response = homeLandingService.getLanding(null);

        assertThat(response.getFeaturedPost()).isNull();
        assertThat(response.getEditorPicks()).isEmpty();
        assertThat(response.getTrendingPosts()).isEmpty();
        assertThat(response.getLiveActivity()).isEmpty();
        assertThat(response.getBoards()).hasSize(1);
        assertThat(response.getStats().getBoardCount()).isEqualTo(1);
        assertThat(response.getStats().getPostCount()).isZero();
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
        return new BoardListResponse(board, 10L, "admin", false);
    }
}
