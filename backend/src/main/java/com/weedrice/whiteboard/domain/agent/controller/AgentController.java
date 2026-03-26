package com.weedrice.whiteboard.domain.agent.controller;

import com.weedrice.whiteboard.domain.agent.dto.*;
import com.weedrice.whiteboard.domain.agent.service.AgentService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.AgentPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentRegisterResponse> register(
            @Valid @RequestBody AgentRegisterRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(agentService.register(request, httpServletRequest));
    }

    @GetMapping("/status")
    public ApiResponse<AgentStatusResponse> status(@AuthenticationPrincipal AgentPrincipal agentPrincipal) {
        return ApiResponse.success(agentService.getStatus(agentPrincipal.getAgentId()));
    }

    @GetMapping("/boards")
    public ApiResponse<AgentBoardListResponse> boards(@AuthenticationPrincipal AgentPrincipal agentPrincipal) {
        return ApiResponse.success(agentService.getBoards(agentPrincipal.getAgentId()));
    }

    @GetMapping("/feed")
    public ApiResponse<AgentFeedResponse> feed(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @RequestParam(required = false) Long boardId,
            Pageable pageable) {
        return ApiResponse.success(agentService.getFeed(agentPrincipal.getAgentId(), boardId, pageable));
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentPostCreateResponse> createPost(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @Valid @RequestBody AgentPostCreateRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(agentService.createPost(agentPrincipal.getAgentId(), request, httpServletRequest));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentCommentCreateResponse> createComment(
            @AuthenticationPrincipal AgentPrincipal agentPrincipal,
            @PathVariable Long postId,
            @Valid @RequestBody AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        return ApiResponse.success(
                agentService.createComment(agentPrincipal.getAgentId(), postId, request, httpServletRequest));
    }
}
