package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardDefaultCategoryResolver;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentBoardAccessService {

    private final AdminRepository adminRepository;
    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final PostService postService;

    public Set<Long> resolveWritableBoardIds(Agent agent, List<Board> boards,
            Map<Long, List<CategoryResponse>> categoriesByBoardId) {
        if (agent == null || agent.getUser() == null || boards == null || boards.isEmpty()) {
            return Collections.emptySet();
        }

        User user = agent.getUser();
        List<Long> boardIds = boards.stream()
                .map(Board::getBoardId)
                .toList();
        Set<Long> boardAdminIds = resolveBoardAdminIds(user, boards, boardIds);

        return boards.stream()
                .filter(board -> Boolean.TRUE.equals(board.getIsActive()))
                .filter(board -> Boolean.TRUE.equals(board.getIsPublic()))
                .filter(Board::isAgentEnabled)
                .filter(board -> hasRequiredWriteRole(board, user, boardAdminIds,
                        categoriesByBoardId.getOrDefault(board.getBoardId(), List.of())))
                .map(Board::getBoardId)
                .collect(Collectors.toSet());
    }

    public void validateAgentBoardWritable(Agent agent, Board board) {
        if (!canAgentWriteBoard(agent, board, new HashMap<>())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent access is disabled for this board");
        }
    }

    public void validateAgentBoardReadable(Agent agent, Board board) {
        if (!canAgentReadBoard(agent, board)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent access is disabled for this board");
        }
    }

    public boolean canViewSecretPosts(Agent agent, Board board) {
        if (agent == null || agent.getUser() == null || board == null || board.getBoardId() == null) {
            return false;
        }
        return resolveBoardAdminIds(
                agent.getUser(),
                List.of(board),
                List.of(board.getBoardId()))
                .contains(board.getBoardId());
    }

    public List<Board> getAccessibleFeedBoards(Agent agent, Long boardId) {
        if (boardId != null) {
            return boardRepository.findByBoardId(boardId)
                    .filter(board -> canAgentWriteBoard(agent, board, new HashMap<>()))
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }

        List<Board> candidateBoards = boardRepository
                .findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true);
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

    public Set<Long> resolveBoardAdminIds(User user, List<Board> boards, List<Long> boardIds) {
        if (user == null || boards == null || boards.isEmpty()) {
            return Collections.emptySet();
        }
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return boards.stream()
                    .map(Board::getBoardId)
                    .collect(Collectors.toSet());
        }

        Set<Long> boardAdminIds = adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, boardIds, true)
                .stream()
                .map(admin -> admin.getBoard().getBoardId())
                .collect(Collectors.toSet());
        boards.stream()
                .filter(board -> board.getCreator() != null
                        && Objects.equals(board.getCreator().getUserId(), user.getUserId()))
                .map(Board::getBoardId)
                .forEach(boardAdminIds::add);
        return boardAdminIds;
    }

    private boolean hasRequiredWriteRole(Board board, User user, Set<Long> boardAdminIds,
            List<CategoryResponse> categories) {
        if (board == null || user == null) {
            return false;
        }

        String minWriteRole = BoardDefaultCategoryResolver.resolveDefaultCategoryResponse(categories)
                .map(CategoryResponse::getMinWriteRole)
                .orElse(null);

        if (Role.SUPER_ADMIN.equals(minWriteRole)) {
            return Boolean.TRUE.equals(user.getIsSuperAdmin());
        }
        if (Role.BOARD_ADMIN.equals(minWriteRole)) {
            return boardAdminIds.contains(board.getBoardId());
        }
        return true;
    }

    private boolean canAgentWriteBoard(Agent agent, Board board, Map<Long, Boolean> writableBoardCache) {
        if (agent == null || board == null) {
            return false;
        }
        if (!board.getIsActive() || !board.getIsPublic() || !board.isAgentEnabled()) {
            return false;
        }
        return writableBoardCache.computeIfAbsent(
                board.getBoardId(),
                ignored -> postService.canWriteToBoard(agent.getUser().getUserId(), board));
    }

    private boolean canAgentReadBoard(Agent agent, Board board) {
        return agent != null
                && agent.getUser() != null
                && board != null
                && Boolean.TRUE.equals(board.getIsActive())
                && Boolean.TRUE.equals(board.getIsPublic())
                && board.isAgentEnabled();
    }
}
