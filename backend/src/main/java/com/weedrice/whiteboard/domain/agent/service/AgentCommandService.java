package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostDeleteResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentCreateContext;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostCreateContext;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.post.service.PostTitleValidator;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentAuditService agentAuditService;
    private final AgentQuotaService agentQuotaService;
    private final AgentLinkBuilder agentLinkBuilder;

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        Board board = boardRepository.findByBoardUrlForUpdate(request.getBoardUrl())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        agentBoardAccessService.validateAgentBoardReadable(agent, board);
        BoardCategory category = resolveCategory(board, request.getCategoryId());
        agentBoardAccessService.validateAgentBoardWritable(agent, board, category);
        PostTitleValidator.validate(request.getTitle());
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
        Long postId = postService.createPostAsAgent(
                agent.getUser().getUserId(),
                agentId,
                postCreateRequest,
                PostCreateContext.agent(agent, board, category));
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_POST,
                AgentAuditTargetType.POST,
                postId,
                requestContext);
        return new AgentPostCreateResponse(postId, agentLinkBuilder.postUrl(postId));
    }

    @Transactional
    public AgentPostDeleteResponse deletePost(Long agentId, Long postId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgent(agentId);
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.getAgent() == null || !Objects.equals(post.getAgent().getAgentId(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boolean alreadyDeleted = Boolean.TRUE.equals(post.getIsDeleted());
        LocalDateTime deletedAt = alreadyDeleted && post.getModifiedAt() != null
                ? post.getModifiedAt()
                : LocalDateTime.now(KST);
        if (!alreadyDeleted) {
            post.deletePost();
            agentAuditService.saveLog(
                    agent,
                    agent.getUser(),
                    AgentAuditActionType.DELETE_POST,
                    AgentAuditTargetType.POST,
                    postId,
                    requestContext);
        }

        return new AgentPostDeleteResponse(
                postId,
                true,
                alreadyDeleted ? true : null,
                toOffsetDateTime(deletedAt));
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, Long postId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        agentQuotaService.reserveCommentCreation(agent);
        Long commentId = commentService.createCommentAsAgent(agent.getUser().getUserId(), agentId, postId, null,
                request.getContent(), CommentCreateContext.agentRoot(agent, post));
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                commentId,
                requestContext);
        return new AgentCommentCreateResponse(commentId);
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, Long commentId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgentForUpdate(agentId);
        Comment parentComment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (parentComment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        agentBoardAccessService.validateAgentBoardWritable(agent, parentComment.getPost().getBoard());
        agentQuotaService.reserveCommentCreation(agent);
        Long replyId = commentService.createCommentAsAgent(
                agent.getUser().getUserId(),
                agentId,
                parentComment.getPost().getPostId(),
                commentId,
                request.getContent(),
                CommentCreateContext.agentReply(agent, parentComment));
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                replyId,
                requestContext);
        return new AgentCommentCreateResponse(replyId);
    }

    @Transactional
    public AgentPostLikeResponse likePost(Long agentId, Long postId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        int likeCount = postService.likePost(agent.getUser().getUserId(), agent, post);
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.LIKE_POST,
                AgentAuditTargetType.POST,
                postId,
                requestContext);
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

    private BoardCategory resolveCategory(Board board, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(
                        categoryId,
                        board.getBoardId(),
                        true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }
}
