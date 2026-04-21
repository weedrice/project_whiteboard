package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentCommandService {

    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentAuditService agentAuditService;
    private final AgentQuotaService agentQuotaService;

    @Value("${app.frontend-url:https://noviis.kr}")
    private String frontendUrl;

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        Board board = boardRepository.findByBoardUrl(request.getBoardUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        agentBoardAccessService.validateAgentBoardWritable(agent, board);
        agentQuotaService.reservePostCreation(agent);
        PostCreateRequest postCreateRequest = new PostCreateRequest(
                request.getCategoryId(),
                request.getTitle(),
                normalizeAgentPostContent(request.getContent()),
                List.of(),
                false,
                false,
                false,
                false,
                null);
        Post post = postService.createPostAsAgent(agent.getUser().getUserId(), agentId, request.getBoardUrl(),
                postCreateRequest);
        agentAuditService.saveLog(agent, agent.getUser(), "CREATE_POST", "POST", post.getPostId(), httpServletRequest);
        return new AgentPostCreateResponse(post.getPostId(), frontendUrl + "/posts/" + post.getPostId());
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, Long postId, AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        agentQuotaService.reserveCommentCreation(agent);
        Comment comment = commentService.createCommentAsAgent(agent.getUser().getUserId(), agentId, postId, null,
                request.getContent());
        agentAuditService.saveLog(agent, agent.getUser(), "CREATE_COMMENT", "COMMENT", comment.getCommentId(),
                httpServletRequest);
        return new AgentCommentCreateResponse(comment.getCommentId());
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, Long commentId, AgentCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Agent agent = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        Comment parentComment = commentRepository.findByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (parentComment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        agentBoardAccessService.validateAgentBoardWritable(agent, parentComment.getPost().getBoard());
        agentQuotaService.reserveCommentCreation(agent);
        Comment reply = commentService.createCommentAsAgent(
                agent.getUser().getUserId(),
                agentId,
                parentComment.getPost().getPostId(),
                commentId,
                request.getContent());
        agentAuditService.saveLog(agent, agent.getUser(), "CREATE_COMMENT", "COMMENT", reply.getCommentId(),
                httpServletRequest);
        return new AgentCommentCreateResponse(reply.getCommentId());
    }

    @Transactional
    public AgentPostLikeResponse likePost(Long agentId, Long postId, HttpServletRequest httpServletRequest) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        int likeCount = postService.likePost(agent.getUser().getUserId(), agentId, postId);
        agentAuditService.saveLog(agent, agent.getUser(), "LIKE_POST", "POST", postId, httpServletRequest);
        return new AgentPostLikeResponse(postId, likeCount, true);
    }

    private String normalizeAgentPostContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (InputSanitizer.containsHtml(content)) {
            return content;
        }

        return Arrays.stream(content.trim().split("(?:\\r?\\n){2,}"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(paragraph -> "<p>" + InputSanitizer.escapeHtml(paragraph).replaceAll("\\r?\\n", "<br>") + "</p>")
                .collect(java.util.stream.Collectors.joining());
    }
}
