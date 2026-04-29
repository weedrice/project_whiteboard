package com.weedrice.whiteboard.domain.agent.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentCommandService;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.service.AgentQueryService;
import com.weedrice.whiteboard.domain.agent.service.AgentRequestContext;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.AgentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentLifecycleService agentLifecycleService;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private AgentCommandService agentCommandService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AgentController agentController;

    private final AgentPrincipal agentPrincipal = new AgentPrincipal(7L, 1L, "Writer Agent", "ACTIVE");

    @Test
    @DisplayName("Agent 등록 API 성공")
    void register_success() {
        AgentRegisterRequest request = new AgentRegisterRequest();
        ReflectionTestUtils.setField(request, "description", "Writes posts");

        given(agentLifecycleService.register(any(AgentRegisterRequest.class)))
                .willReturn(new AgentRegisterResponse("noviis_agt_token"));

        ApiResponse<AgentRegisterResponse> response = agentController.register(request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getAgentToken()).isEqualTo("noviis_agt_token");
    }

    @Test
    @DisplayName("Agent status API 성공")
    void status_success() {
        AgentStatusResponse responseBody = AgentStatusResponse.builder()
                .status("active")
                .name("Writer Agent")
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(2)
                        .commentsToday(5)
                        .resetAt(OffsetDateTime.now())
                        .build())
                .build();

        given(agentQueryService.getStatus(7L)).willReturn(responseBody);

        ApiResponse<AgentStatusResponse> response = agentController.status(agentPrincipal);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getStatus()).isEqualTo("active");
        assertThat(response.getData().getStats().getPostsToday()).isEqualTo(2);
    }

    @Test
    @DisplayName("Agent feed API 성공")
    void feed_success() {
        AgentPostListItem item = AgentPostListItem.builder()
                .postId(101L)
                .title("Test Post")
                .boardId(3L)
                .boardUrl("free")
                .commentCount(4)
                .createdAt(LocalDateTime.now())
                .hasMyComment(true)
                .build();

        given(agentQueryService.getFeed(7L, 3L, PageRequest.of(0, 10)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        ApiResponse<PageResponse<AgentPostListItem>> response = agentController.feed(agentPrincipal, 3L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).hasSize(1);
        assertThat(response.getData().getContent().get(0).isHasMyComment()).isTrue();
    }

    @Test
    @DisplayName("Agent 게시글 작성 API 성공")
    void createPost_success() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "Agent title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        given(agentCommandService.createPost(eq(7L), any(AgentPostCreateRequest.class), any(AgentRequestContext.class)))
                .willReturn(new AgentPostCreateResponse(101L, "https://noviis.kr/posts/101"));

        ApiResponse<AgentPostCreateResponse> response = agentController.createPost(
                agentPrincipal, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getPostId()).isEqualTo(101L);
        assertThat(response.getData().getUrl()).contains("/posts/101");
    }

    @Test
    @DisplayName("Agent 댓글 작성 API 성공")
    void createComment_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));

        given(agentCommandService.createComment(eq(7L), eq(101L), any(AgentCommentCreateRequest.class),
                any(AgentRequestContext.class)))
                .willReturn(new AgentCommentCreateResponse(301L));

        ApiResponse<AgentCommentCreateResponse> response = agentController.createComment(
                agentPrincipal, 101L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCommentId()).isEqualTo(301L);
    }

    @Test
    @DisplayName("Agent boards API 성공")
    void boards_success() {
        given(agentQueryService.getBoards(7L))
                .willReturn(new AgentBoardListResponse(List.of(
                        AgentBoardItem.builder().boardId(3L).boardName("Free").boardUrl("free").build())));

        ApiResponse<AgentBoardListResponse> response = agentController.boards(agentPrincipal);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getBoards()).hasSize(1);
        assertThat(response.getData().getBoards().get(0).getBoardId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Agent 내 게시글 API 성공")
    void myPosts_success() {
        AgentPostListItem item = AgentPostListItem.builder()
                .postId(201L)
                .title("My post")
                .boardId(3L)
                .boardUrl("free")
                .createdAt(LocalDateTime.now())
                .build();

        given(agentQueryService.getMyPosts(7L, PageRequest.of(0, 10)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        ApiResponse<PageResponse<AgentPostListItem>> response = agentController.myPosts(agentPrincipal, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).extracting(AgentPostListItem::getPostId).containsExactly(201L);
    }

    @Test
    @DisplayName("Agent 게시판 게시글 API 성공")
    void boardPosts_success() {
        AgentPostListItem item = AgentPostListItem.builder()
                .postId(301L)
                .title("Board post")
                .boardId(3L)
                .boardUrl("free")
                .createdAt(LocalDateTime.now())
                .build();

        given(agentQueryService.getBoardPosts(7L, 3L, 9L, PageRequest.of(0, 10)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        ApiResponse<PageResponse<AgentPostListItem>> response = agentController.boardPosts(
                agentPrincipal, 3L, 9L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).extracting(AgentPostListItem::getPostId).containsExactly(301L);
    }

    @Test
    @DisplayName("Agent 게시글 댓글 API 성공")
    void comments_success() {
        AgentCommentItem item = AgentCommentItem.builder()
                .commentId(401L)
                .content("comment")
                .status(AgentCommentItem.STATUS_ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        given(agentQueryService.getPostComments(7L, 101L, PageRequest.of(0, 10)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        ApiResponse<PageResponse<AgentCommentItem>> response = agentController.comments(
                agentPrincipal, 101L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).extracting(AgentCommentItem::getCommentId).containsExactly(401L);
    }

    @Test
    @DisplayName("Agent 대댓글 작성 API 성공")
    void createReply_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "reply content");

        given(agentCommandService.createReply(eq(7L), eq(301L), any(AgentCommentCreateRequest.class),
                any(AgentRequestContext.class)))
                .willReturn(new AgentCommentCreateResponse(501L));

        ApiResponse<AgentCommentCreateResponse> response = agentController.createReply(
                agentPrincipal, 301L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCommentId()).isEqualTo(501L);
    }

    @Test
    @DisplayName("Agent 게시글 좋아요 API 성공")
    void likePost_success() {
        given(agentCommandService.likePost(eq(7L), eq(101L), any(AgentRequestContext.class)))
                .willReturn(new AgentPostLikeResponse(101L, 11, true));

        ApiResponse<AgentPostLikeResponse> response = agentController.likePost(agentPrincipal, 101L, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getPostId()).isEqualTo(101L);
        assertThat(response.getData().getLikeCount()).isEqualTo(11);
    }
}
