package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
class PostCreateTargetResolver {

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;

    public PostCreateTarget resolveTarget(Long userId, Long agentId, Long boardId, PostCreateContext context) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = resolveAgent(userId, agentId, context);
        Board board = resolveBoard(boardId, context);
        return new PostCreateTarget(user, agent, board, isBoardWritablePrevalidated(context));
    }

    public PostCreateTarget resolveTargetByBoardUrl(Long userId, Long agentId, String boardUrl) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = resolveAgent(userId, agentId, null);
        Board board = boardRepository.findByBoardUrlForUpdate(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return new PostCreateTarget(user, agent, board, false);
    }

    public PostCreateCategoryTarget resolveCategory(Board board, Long categoryId, PostCreateContext context) {
        BoardCategory category = resolveCreatedCategory(board, categoryId, context);
        return new PostCreateCategoryTarget(
                category,
                isCategoryWriteRolePrevalidated(context, categoryId, category));
    }

    private boolean isBoardWritablePrevalidated(PostCreateContext context) {
        return context != null && context.boardWritablePrevalidated();
    }

    private boolean isCategoryWriteRolePrevalidated(PostCreateContext context, Long categoryId,
            BoardCategory category) {
        if (!isBoardWritablePrevalidated(context)) {
            return false;
        }
        if (categoryId == null) {
            return category == null;
        }
        return category != null
                && context.category() != null
                && Objects.equals(context.category().getCategoryId(), categoryId)
                && Objects.equals(context.category().getCategoryId(), category.getCategoryId());
    }

    private Agent resolveAgent(Long userId, Long agentId, PostCreateContext context) {
        if (context != null && context.agent() != null) {
            Agent contextAgent = context.agent();
            if (!Objects.equals(contextAgent.getAgentId(), agentId)
                    || contextAgent.getUser() == null
                    || !Objects.equals(contextAgent.getUser().getUserId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return contextAgent;
        }
        if (agentId == null) {
            return null;
        }
        return agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
    }

    private Board resolveBoard(Long boardId, PostCreateContext context) {
        if (context != null && context.board() != null) {
            Board contextBoard = context.board();
            if (boardId != null && !Objects.equals(contextBoard.getBoardId(), boardId)) {
                throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
            }
            return boardRepository.findByIdForUpdate(contextBoard.getBoardId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        }
        if (boardId == null) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        return boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    }

    private BoardCategory resolveCreatedCategory(Board board, Long categoryId, PostCreateContext context) {
        if (context != null && context.category() != null) {
            BoardCategory contextCategory = context.category();
            if (!Objects.equals(contextCategory.getCategoryId(), categoryId)
                    || contextCategory.getBoard() == null
                    || !Objects.equals(contextCategory.getBoard().getBoardId(), board.getBoardId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return contextCategory;
        }
        if (categoryId == null) {
            return null;
        }
        return findActiveCategory(board, categoryId);
    }

    private BoardCategory findActiveCategory(Board board, Long categoryId) {
        if (board == null || categoryId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(categoryId, board.getBoardId(), true)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
