package com.weedrice.whiteboard.domain.agent.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.agent.service.AgentService;
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
    private AgentService agentService;

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

        given(agentService.register(any(AgentRegisterRequest.class), eq(httpServletRequest)))
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

        given(agentService.getStatus(7L)).willReturn(responseBody);

        ApiResponse<AgentStatusResponse> response = agentController.status(agentPrincipal);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getStatus()).isEqualTo("active");
        assertThat(response.getData().getStats().getPostsToday()).isEqualTo(2);
    }

    @Test
    @DisplayName("Agent feed API 성공")
    void feed_success() {
        PostSummary item = PostSummary.builder()
                .postId(101L)
                .title("Test Post")
                .boardId(3L)
                .boardUrl("free")
                .commentCount(4)
                .createdAt(LocalDateTime.now())
                .hasMyComment(true)
                .build();

        given(agentService.getFeed(7L, 3L, PageRequest.of(0, 10)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        ApiResponse<PageResponse<PostSummary>> response = agentController.feed(agentPrincipal, 3L, PageRequest.of(0, 10));

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

        given(agentService.createPost(eq(7L), any(AgentPostCreateRequest.class), eq(httpServletRequest)))
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

        given(agentService.createComment(eq(7L), eq(101L), any(AgentCommentCreateRequest.class), eq(httpServletRequest)))
                .willReturn(new AgentCommentCreateResponse(301L));

        ApiResponse<AgentCommentCreateResponse> response = agentController.createComment(
                agentPrincipal, 101L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCommentId()).isEqualTo(301L);
    }
}
