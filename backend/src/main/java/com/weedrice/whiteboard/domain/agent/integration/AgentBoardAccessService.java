package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentBoardAccessPort;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardDefaultCategoryResolver;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentBoardAccessService implements AgentBoardAccessPort {

    private final AdminRepository adminRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final UserRepository userRepository;

    public Set<Long> resolveWritableBoardIds(Agent agent, List<Board> boards,
            Map<Long, List<CategoryResponse>> categoriesByBoardId) {
        return resolveBoardAccess(agent, boards, categoriesByBoardId).writableBoardIds();
    }

    @Override
    public BoardAccess resolveBoardAccess(Agent agent, List<Board> boards,
            Map<Long, List<CategoryResponse>> categoriesByBoardId) {
        if (agent == null || agent.getUserId() == null || boards == null || boards.isEmpty()) {
            return new BoardAccess(Set.of(), Set.of());
        }

        User user = resolveOwner(agent);
        List<Long> boardIds = boards.stream()
                .map(Board::getBoardId)
                .toList();
        Set<Long> boardAdminIds = resolveBoardAdminIds(user, boards, boardIds);

        Set<Long> writableBoardIds = boards.stream()
                .filter(board -> Boolean.TRUE.equals(board.getIsActive()))
                .filter(board -> Boolean.TRUE.equals(board.getIsPublic()))
                .filter(Board::isAgentEnabled)
                .filter(board -> canWriteDefaultCategory(board, user, boardAdminIds,
                        categoriesByBoardId.getOrDefault(board.getBoardId(), List.of())))
                .map(Board::getBoardId)
                .collect(Collectors.toSet());
        return new BoardAccess(writableBoardIds, boardAdminIds);
    }

    public void validateAgentBoardWritable(Agent agent, Board board) {
        if (!canAgentWriteBoard(agent, board)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent access is disabled for this board");
        }
    }

    public void validateAgentBoardWritable(Agent agent, Board board, BoardCategory category) {
        if (!canAgentWriteBoard(agent, board, category)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent access is disabled for this board");
        }
    }

    public void validateAgentBoardReadable(Agent agent, Board board) {
        if (!canAgentReadBoard(agent, board)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent access is disabled for this board");
        }
    }

    public boolean canViewSecretPosts(Agent agent, Board board) {
        if (agent == null || agent.getUserId() == null || board == null || board.getBoardId() == null) {
            return false;
        }
        return resolveBoardAdminIds(agent, List.of(board), List.of(board.getBoardId()))
                .contains(board.getBoardId());
    }

    public List<Board> getAccessibleFeedBoards(Agent agent, Long boardId) {
        if (boardId != null) {
            return boardRepository.findByBoardId(boardId)
                    .filter(board -> canAgentWriteBoard(agent, board))
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }

        List<Board> candidateBoards = boardRepository
                .findByIsActiveTrueAndIsPublicTrueAndAgentUseYnTrueOrderBySortOrderAscBoardIdAsc();
        if (candidateBoards.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<CategoryResponse>> categoriesByBoardId = loadCategoriesByBoardIds(candidateBoards.stream()
                .map(Board::getBoardId)
                .toList());
        Set<Long> writableBoardIds = resolveWritableBoardIds(agent, candidateBoards, categoriesByBoardId);

        return candidateBoards.stream()
                .filter(board -> writableBoardIds.contains(board.getBoardId()))
                .toList();
    }

    public Map<Long, List<CategoryResponse>> loadCategoriesByBoardIds(List<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(
                        boardIds, true)
                .stream()
                .collect(Collectors.groupingBy(
                        category -> category.getBoard().getBoardId(),
                        Collectors.mapping(CategoryResponse::new, Collectors.toList())));
    }

    public Set<Long> resolveBoardAdminIds(Agent agent, List<Board> boards, List<Long> boardIds) {
        User user = agent == null || agent.getUserId() == null ? null : resolveOwner(agent);
        return resolveBoardAdminIds(user, boards, boardIds);
    }

    private Set<Long> resolveBoardAdminIds(User user, List<Board> boards, List<Long> boardIds) {
        if (user == null || boards == null || boards.isEmpty()) {
            return Collections.emptySet();
        }
        if (user.isUsableSuperAdmin()) {
            return boards.stream()
                    .map(Board::getBoardId)
                    .collect(Collectors.toSet());
        }

        return adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, boardIds, true)
                .stream()
                .map(admin -> admin.getBoard().getBoardId())
                .collect(Collectors.toSet());
    }

    private boolean canWriteDefaultCategory(Board board, User user, Set<Long> boardAdminIds,
            List<CategoryResponse> categories) {
        if (board == null || user == null) {
            return false;
        }

        String minWriteRole = BoardDefaultCategoryResolver.resolveDefaultCategoryResponse(categories)
                .map(CategoryResponse::getMinWriteRole)
                .orElse(null);

        return postAuthorCommandPolicy.canWriteBoardWithRole(board, user, minWriteRole, boardAdminIds);
    }

    private boolean canAgentWriteBoard(Agent agent, Board board, BoardCategory category) {
        if (agent == null || agent.getUserId() == null || board == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsActive())
                || !Boolean.TRUE.equals(board.getIsPublic())
                || !board.isAgentEnabled()) {
            return false;
        }
        if (category == null) {
            return canAgentWriteBoard(agent, board);
        }
        if (category.getBoard() == null
                || !Objects.equals(category.getBoard().getBoardId(), board.getBoardId())
                || !Boolean.TRUE.equals(category.getIsActive())) {
            return false;
        }
        return postAuthorCommandPolicy.canWriteBoardWithRole(
                board, resolveOwner(agent), category.getMinWriteRole(),
                resolveBoardAdminIds(agent, List.of(board), List.of(board.getBoardId())));
    }

    private boolean canAgentWriteBoard(Agent agent, Board board) {
        if (agent == null || board == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsActive())
                || !Boolean.TRUE.equals(board.getIsPublic())
                || !board.isAgentEnabled()) {
            return false;
        }
        return postAuthorCommandPolicy.canWriteBoardWithDefaultCategory(
                board,
                resolveOwner(agent),
                resolveBoardAdminIds(agent, List.of(board), List.of(board.getBoardId())));
    }

    private boolean canAgentReadBoard(Agent agent, Board board) {
        return agent != null
                && agent.getUserId() != null
                && board != null
                && Boolean.TRUE.equals(board.getIsActive())
                && Boolean.TRUE.equals(board.getIsPublic())
                && board.isAgentEnabled();
    }

    private User resolveOwner(Agent agent) {
        return userRepository.findById(agent.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
