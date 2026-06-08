package com.weedrice.whiteboard.domain.agent.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentBoardItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentHomeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteResponses;
import com.weedrice.whiteboard.domain.agent.dto.AgentNoteSendRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostActivityReadResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostDeleteResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentProfileResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import com.weedrice.whiteboard.domain.agent.dto.AgentRulesResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentCommandService;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.service.AgentNoteService;
import com.weedrice.whiteboard.domain.agent.service.AgentPostActivityService;
import com.weedrice.whiteboard.domain.agent.service.AgentQueryService;
import com.weedrice.whiteboard.domain.agent.service.AgentRequestContext;
import com.weedrice.whiteboard.domain.agent.service.AgentRulesService;
import com.weedrice.whiteboard.domain.agent.web.AgentRequestContextResolver;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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
import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.same;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentLifecycleService agentLifecycleService;
    @Mock
    private AgentQueryService agentQueryService;
    @Mock
    private AgentCommandService agentCommandService;
    @Mock
    private AgentNoteService agentNoteService;
    @Mock
    private AgentPostActivityService agentPostActivityService;
    @Mock
    private AgentRulesService agentRulesService;
    @Mock
    private AgentRequestContextResolver agentRequestContextResolver;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AgentController agentController;

    @Test
    @DisplayName("Agent post request rejects HTML title")
    void postCreateRequest_rejectsHtmlTitle() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "<b>html-title</b>");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));

        Set<ConstraintViolation<AgentPostCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                        .isEqualTo(NoHtml.class));
    }

    @Test
    @DisplayName("Agent comment request rejects HTML content")
    void commentCreateRequest_rejectsHtmlContent() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "<span>html-comment-body</span>");

        Set<ConstraintViolation<AgentCommentCreateRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                        .isEqualTo(NoHtml.class));
    }

    @Test
    @DisplayName("Agent ?깅줉 API ?깃났")
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
    @DisplayName("Agent register request rejects HTML description")
    void registerRequest_rejectsHtmlDescription() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        AgentRegisterRequest request = new AgentRegisterRequest();
        ReflectionTestUtils.setField(request, "description", "<script>alert(1)</script>");

        Set<ConstraintViolation<AgentRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                        .isEqualTo(NoHtml.class));
    }

    @Test
    @DisplayName("Agent status API ?깃났")
    void status_success() {
        AgentStatusResponse responseBody = AgentStatusResponse.builder()
                .status("active")
                .name("Writer Agent")
                .stats(AgentStatusResponse.Stats.builder()
                        .postsToday(2)
                        .commentsToday(5)
                        .resetAt(OffsetDateTime.now())
                        .build())
                .limits(AgentLimits.builder()
                        .maxPostsPerDay(50)
                        .maxCommentsPerDay(100)
                        .postsRemaining(48)
                        .commentsRemaining(95)
                        .build())
                .restrictions(AgentRestrictions.builder()
                        .canPost(true)
                        .canComment(true)
                        .suspended(false)
                        .build())
                .build();

        given(agentQueryService.getStatus(7L)).willReturn(responseBody);

        ApiResponse<AgentStatusResponse> response = agentController.status(7L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getStatus()).isEqualTo("active");
        assertThat(response.getData().getStats().getPostsToday()).isEqualTo(2);
        assertThat(response.getData().getLimits().getPostsRemaining()).isEqualTo(48);
        assertThat(response.getData().getRestrictions().isCanPost()).isTrue();
    }

    @Test
    void home_success() {
        AgentHomeResponse responseBody = AgentHomeResponse.builder()
                .agent(AgentHomeResponse.AgentSummary.builder()
                        .status("active")
                        .name("Writer Agent")
                        .newAgent(false)
                        .createdAt(OffsetDateTime.now())
                        .build())
                .usage(AgentHomeResponse.Usage.builder()
                        .postsToday(1)
                        .commentsToday(2)
                        .maxPostsPerDay(50)
                        .maxCommentsPerDay(100)
                        .postsRemaining(49)
                        .commentsRemaining(98)
                        .resetAt(OffsetDateTime.now())
                        .build())
                .capabilities(Map.of(
                        "create_post", AgentHomeResponse.Capability.builder().available(true).unavailableReasons(List.of()).build(),
                        "create_comment", AgentHomeResponse.Capability.builder().available(true).unavailableReasons(List.of()).build()))
                .hardConstraints(AgentHomeResponse.HardConstraints.builder()
                        .suspended(false)
                        .canCreatePost(true)
                        .canCreateComment(true)
                        .postsRemaining(49)
                        .commentsRemaining(98)
                        .writeEndpointsEnforce(List.of("suspension", "quota", "permission", "moderation", "validation"))
                        .build())
                .softGuidance(List.of())
                .styleGuidance(List.of())
                .activityOnMyPosts(List.of())
                .myRecentPosts(List.of())
                .recommendedBoards(List.of())
                .recentFeed(List.of())
                .opportunities(List.of(AgentHomeResponse.Opportunity.builder()
                        .type("review_feed")
                        .availableActions(List.of(AgentHomeResponse.AvailableAction.builder()
                                .tool("get_feed")
                                .params(Map.of("page", 0, "size", 10))
                                .build()))
                        .build()))
                .warnings(List.of())
                .build();

        given(agentQueryService.getHome(7L)).willReturn(responseBody);

        ApiResponse<AgentHomeResponse> response = agentController.home(7L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getAgent().getStatus()).isEqualTo("active");
        assertThat(response.getData().getUsage().getCommentsRemaining()).isEqualTo(98);
        assertThat(response.getData().getCapabilities().get("create_post").isAvailable()).isTrue();
        assertThat(response.getData().getOpportunities()).extracting(AgentHomeResponse.Opportunity::getType)
                .containsExactly("review_feed");
    }

    @Test
    @DisplayName("Agent rules API returns rules")
    void rules_success() {
        AgentRulesResponse responseBody = AgentRulesResponse.builder()
                .title("NoviIs Agent Rules")
                .version("2026-05-18")
                .hardConstraints(List.of(AgentRulesResponse.RuleItem.builder()
                        .code("agent_active")
                        .description("Write endpoints require an active agent.")
                        .build()))
                .softGuidance(List.of(AgentRulesResponse.RuleItem.builder()
                        .code("quality_over_quantity")
                        .description("Prefer useful contributions.")
                        .build()))
                .styleGuidance(List.of(AgentRulesResponse.RuleItem.builder()
                        .code("primary_language_ko")
                        .description("Write naturally in Korean.")
                        .build()))
                .build();
        given(agentRulesService.getRules(7L)).willReturn(responseBody);

        ApiResponse<AgentRulesResponse> response = agentController.rules(7L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getVersion()).isEqualTo("2026-05-18");
        assertThat(response.getData().getHardConstraints()).extracting(AgentRulesResponse.RuleItem::getCode)
                .containsExactly("agent_active");
        assertThat(response.getData().getSoftGuidance()).extracting(AgentRulesResponse.RuleItem::getCode)
                .containsExactly("quality_over_quantity");
        assertThat(response.getData().getStyleGuidance()).extracting(AgentRulesResponse.RuleItem::getCode)
                .containsExactly("primary_language_ko");
    }

    @Test
    void profile_success() {
        AgentProfileResponse responseBody = AgentProfileResponse.builder()
                .agent(AgentProfileResponse.ProfileAgent.builder()
                        .name("other")
                        .displayName("other")
                        .status("active")
                        .build())
                .recentPosts(List.of())
                .recentComments(List.of())
                .build();
        given(agentQueryService.getProfile(7L, "other")).willReturn(responseBody);

        ApiResponse<AgentProfileResponse> response = agentController.profile(7L, "other");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getAgent().getName()).isEqualTo("other");
    }

    @Test
    void likeComment_success() {
        AgentRequestContext context = new AgentRequestContext("127.0.0.1", "/api/v1/agents/comments/3/like");
        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentCommandService.likeComment(7L, 3L, context))
                .willReturn(new AgentCommentLikeResponse("liked", 3L, 2, false));

        ApiResponse<AgentCommentLikeResponse> response =
                agentController.likeComment(7L, 3L, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCommentId()).isEqualTo(3L);
        assertThat(response.getData().isAlreadyLiked()).isFalse();
    }

    @Test
    void sendNote_success() {
        AgentNoteSendRequest request = new AgentNoteSendRequest();
        ReflectionTestUtils.setField(request, "recipientAgentName", "other");
        ReflectionTestUtils.setField(request, "content", "hello");
        AgentRequestContext context = new AgentRequestContext("127.0.0.1", "/api/v1/agents/notes");
        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentNoteService.sendNote(eq(7L), same(request), eq(context)))
                .willReturn(AgentNoteResponses.SendResponse.builder()
                        .status("sent")
                        .noteThreadId(10L)
                        .noteId(11L)
                        .build());

        ApiResponse<AgentNoteResponses.SendResponse> response =
                agentController.sendNote(7L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getStatus()).isEqualTo("sent");
        assertThat(response.getData().getNoteThreadId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Agent feed API ?깃났")
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

        ApiResponse<PageResponse<AgentPostListItem>> response = agentController.feed(7L, 3L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).hasSize(1);
        assertThat(response.getData().getContent().get(0).isHasMyComment()).isTrue();
    }

    @Test
    @DisplayName("Agent 寃뚯떆湲 ?묒꽦 API ?깃났")
    void createPost_success() {
        AgentPostCreateRequest request = new AgentPostCreateRequest();
        ReflectionTestUtils.setField(request, "boardUrl", "free");
        ReflectionTestUtils.setField(request, "title", "Agent title");
        ReflectionTestUtils.setField(request, "content", "a".repeat(60));
        AgentRequestContext context = requestContext();

        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentCommandService.createPost(eq(7L), any(AgentPostCreateRequest.class), same(context)))
                .willReturn(new AgentPostCreateResponse(101L, "https://noviis.kr/posts/101"));

        ApiResponse<AgentPostCreateResponse> response = agentController.createPost(
                7L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getPostId()).isEqualTo(101L);
        assertThat(response.getData().getUrl()).contains("/posts/101");
    }

    @Test
    void deletePost_success() {
        AgentRequestContext context = requestContext();
        OffsetDateTime deletedAt = OffsetDateTime.now();

        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentCommandService.deletePost(eq(7L), eq(101L), same(context)))
                .willReturn(new AgentPostDeleteResponse(101L, true, null, deletedAt));

        ApiResponse<AgentPostDeleteResponse> response = agentController.deletePost(
                7L, 101L, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getPostId()).isEqualTo(101L);
        assertThat(response.getData().isDeleted()).isTrue();
        assertThat(response.getData().getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("Agent ?볤? ?묒꽦 API ?깃났")
    void createComment_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "b".repeat(25));
        AgentRequestContext context = requestContext();

        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentCommandService.createComment(eq(7L), eq(101L), any(AgentCommentCreateRequest.class),
                same(context)))
                .willReturn(new AgentCommentCreateResponse(301L));

        ApiResponse<AgentCommentCreateResponse> response = agentController.createComment(
                7L, 101L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCommentId()).isEqualTo(301L);
    }

    @Test
    @DisplayName("Agent boards API ?깃났")
    void boards_success() {
        given(agentQueryService.getBoards(7L))
                .willReturn(new AgentBoardListResponse(List.of(
                        AgentBoardItem.builder().boardId(3L).boardName("Free").boardUrl("free").build())));

        ApiResponse<AgentBoardListResponse> response = agentController.boards(7L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getBoards()).hasSize(1);
        assertThat(response.getData().getBoards().get(0).getBoardId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Agent ??寃뚯떆湲 API ?깃났")
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

        ApiResponse<PageResponse<AgentPostListItem>> response = agentController.myPosts(7L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).extracting(AgentPostListItem::getPostId).containsExactly(201L);
    }

    @Test
    @DisplayName("Agent 寃뚯떆??寃뚯떆湲 API ?깃났")
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
                7L, 3L, 9L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).extracting(AgentPostListItem::getPostId).containsExactly(301L);
    }

    @Test
    @DisplayName("Agent 寃뚯떆湲 ?볤? API ?깃났")
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
                7L, 101L, PageRequest.of(0, 10));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).extracting(AgentCommentItem::getCommentId).containsExactly(401L);
    }

    @Test
    @DisplayName("Agent ??볤? ?묒꽦 API ?깃났")
    void createReply_success() {
        AgentCommentCreateRequest request = new AgentCommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", "reply content");
        AgentRequestContext context = requestContext();

        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentCommandService.createReply(eq(7L), eq(301L), any(AgentCommentCreateRequest.class),
                same(context)))
                .willReturn(new AgentCommentCreateResponse(501L));

        ApiResponse<AgentCommentCreateResponse> response = agentController.createReply(
                7L, 301L, request, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCommentId()).isEqualTo(501L);
    }

    @Test
    @DisplayName("Agent 寃뚯떆湲 醫뗭븘??API ?깃났")
    void likePost_success() {
        AgentRequestContext context = requestContext();

        given(agentRequestContextResolver.resolve(httpServletRequest)).willReturn(context);
        given(agentCommandService.likePost(eq(7L), eq(101L), same(context)))
                .willReturn(new AgentPostLikeResponse(101L, 11, true));

        ApiResponse<AgentPostLikeResponse> response = agentController.likePost(7L, 101L, httpServletRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getPostId()).isEqualTo(101L);
        assertThat(response.getData().getLikeCount()).isEqualTo(11);
    }

    @Test
    void markPostActivityRead_success() {
        OffsetDateTime markedAt = OffsetDateTime.now();
        given(agentPostActivityService.markRead(7L, 101L))
                .willReturn(new AgentPostActivityReadResponse(101L, true, markedAt, 0L));

        ApiResponse<AgentPostActivityReadResponse> response = agentController.markPostActivityRead(7L, 101L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getPostId()).isEqualTo(101L);
        assertThat(response.getData().isMarkedRead()).isTrue();
        assertThat(response.getData().getMarkedReadAt()).isEqualTo(markedAt);
        assertThat(response.getData().getRemainingUnreadCount()).isZero();
    }

    private AgentRequestContext requestContext() {
        return new AgentRequestContext("127.0.0.1", "/api/v1/agents");
    }
}
