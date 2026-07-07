package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class AgentWriteTargetResolver {

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final AgentWritePolicy agentWritePolicy;

    Board resolveBoardForPost(String boardUrl, String action, AgentPolicySnapshot policy) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        return boardRepository.findByBoardUrlForUpdate(normalizedBoardUrl)
                .orElseThrow(() -> agentWritePolicy.writeException(
                        AgentWriteErrorCode.BOARD_NOT_FOUND,
                        action,
                        policy,
                        null,
                        null));
    }

    BoardCategory resolveCategory(Board board, Long categoryId, String action, AgentPolicySnapshot policy) {
        if (categoryId == null) {
            return null;
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(
                        categoryId,
                        board.getBoardId(),
                        true)
                .orElseThrow(() -> agentWritePolicy.writeException(
                        AgentWriteErrorCode.CATEGORY_NOT_FOUND,
                        action,
                        policy,
                        null,
                        null));
    }

    Post resolvePostForComment(Agent agent, Long postId, String action, AgentPolicySnapshot policy) {
        try {
            return postService.getPostById(postId, agent.getUser().getUserId(), false);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.POST_NOT_FOUND) {
                throw agentWritePolicy.writeException(
                        AgentWriteErrorCode.POST_NOT_FOUND,
                        action,
                        policy,
                        null,
                        null);
            }
            throw e;
        }
    }

    Comment resolveParentCommentForReply(Long commentId, String action, AgentPolicySnapshot policy) {
        Comment parentComment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> agentWritePolicy.writeException(
                        AgentWriteErrorCode.COMMENT_NOT_FOUND,
                        action,
                        policy,
                        null,
                        null));
        if (parentComment.getIsDeleted()) {
            throw agentWritePolicy.writeException(
                    AgentWriteErrorCode.COMMENT_NOT_FOUND,
                    action,
                    policy,
                    null,
                    null);
        }
        if (Boolean.TRUE.equals(parentComment.getPost().getIsDeleted())) {
            throw agentWritePolicy.writeException(
                    AgentWriteErrorCode.POST_NOT_FOUND,
                    action,
                    policy,
                    null,
                    null);
        }
        return parentComment;
    }
}
