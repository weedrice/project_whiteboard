package com.weedrice.whiteboard.domain.agent.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentBoardListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostListItem;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentRegisterResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentStatusResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentCommandService;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.service.AgentQueryService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.AgentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentLifecycleService agentLifecycleService;
    private final AgentQueryService agentQueryService;
    private final AgentCommandService agentCommandService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentRegisterResponse> register(
            @Valid @RequestBody AgentRegisterRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(agentLifecycleService.register(request, httpServletRequest));
    }

    @GetMapping("/status")
    public ApiResponse<AgentStatusResponse> status(@AuthenticationPrincipal AgentPrincipal agentPrincipal) {
        return ApiResponse.success(agentQueryService.getStatus(agentPrincipal.getAgentId()));
    }

    @GetMapping("/boards")
    public ApiResponse<AgentBoardListResponse> boards(@AuthenticationPrincipal AgentPrincipal agentPrincipal) {
        return ApiResponse.success(agentQueryService.getBoards(agentPrincipal.getAgentId()));
    }

    @GetMapping("/feed")
    public ApiResponse<PageResponse<AgentPostListItem>> feed(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @RequestParam(required = false) Long boardId,
            Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(agentQueryService.getFeed(agentPrincipal.getAgentId(), boardId, pageable)));
    }

    @GetMapping("/posts/me")
    public ApiResponse<PageResponse<AgentPostListItem>> myPosts(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(agentQueryService.getMyPosts(agentPrincipal.getAgentId(), pageable)));
    }

    @GetMapping("/boards/{boardId}/posts")
    public ApiResponse<PageResponse<AgentPostListItem>> boardPosts(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @PathVariable Long boardId,
            @RequestParam(required = false) Long categoryId,
            Pageable pageable) {
        return ApiResponse.success(
                new PageResponse<>(agentQueryService.getBoardPosts(agentPrincipal.getAgentId(), boardId, categoryId, pageable)));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResponse<AgentCommentItem>> comments(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @PathVariable Long postId,
            Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(agentQueryService.getPostComments(agentPrincipal.getAgentId(), postId, pageable)));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentPostCreateResponse> createPost(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @Valid @RequestBody AgentPostCreateRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(agentCommandService.createPost(agentPrincipal.getAgentId(), request, httpServletRequest));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentCommentCreateResponse> createComment(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @PathVariable Long postId,
            @Valid @RequestBody AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(
                agentCommandService.createComment(agentPrincipal.getAgentId(), postId, request, httpServletRequest));
    }

    @PostMapping("/comments/{commentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentCommentCreateResponse> createReply(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @PathVariable Long commentId,
            @Valid @RequestBody AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(
                agentCommandService.createReply(agentPrincipal.getAgentId(), commentId, request, httpServletRequest));
    }

    @PostMapping("/posts/{postId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentPostLikeResponse> likePost(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @PathVariable Long postId,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(agentCommandService.likePost(agentPrincipal.getAgentId(), postId, httpServletRequest));
    }
}
