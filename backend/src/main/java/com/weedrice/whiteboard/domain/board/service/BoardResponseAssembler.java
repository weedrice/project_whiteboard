package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
class BoardResponseAssembler {

    private final BoardResponseReadService boardResponseReadService;

    BoardDetailResponse assembleDetail(Board board, User currentUser) {
        BoardResponseReadService.DetailReadContext readContext = boardResponseReadService.loadDetail(board, currentUser);
        BoardResponseReadService.ListReadContext listReadContext = readContext.listReadContext();
        Long boardId = board.getBoardId();
        User adminUser = resolveAdminUser(board, listReadContext.boardAdmins().get(boardId));
        boolean isAdmin = currentUser != null && listReadContext.adminBoardIds().contains(boardId);
        boolean isSubscribed = currentUser != null && listReadContext.subscribedBoardIds().contains(boardId);

        return new BoardDetailResponse(
                board,
                listReadContext.subscriberCounts().getOrDefault(boardId, 0L),
                listReadContext.postCounts().getOrDefault(boardId, 0L),
                adminUser.getDisplayName(),
                adminUser.getUserId(),
                isAdmin,
                isSubscribed,
                readContext.categories(),
                readContext.latestPosts(),
                board.isAgentEnabled(),
                isAdmin ? resolveGuidePrompt(board, readContext.guidePrompt()) : null);
    }

    List<BoardListResponse> assembleListAll(List<Board> boards, User currentUser) {
        return assembleListAll(boards, currentUser, null);
    }

    List<BoardListResponse> assembleListAll(List<Board> boards, User currentUser, Map<Long, Long> precomputedPostCounts) {
        return assembleListAll(boards, currentUser, precomputedPostCounts, null);
    }

    List<BoardListResponse> assembleListAllWithSubscribedBoardIds(
            List<Board> boards,
            User currentUser,
            Set<Long> subscribedBoardIds) {
        return assembleListAll(boards, currentUser, null, subscribedBoardIds);
    }

    private List<BoardListResponse> assembleListAll(
            List<Board> boards,
            User currentUser,
            Map<Long, Long> precomputedPostCounts,
            Set<Long> precomputedSubscribedBoardIds) {
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }

        BoardResponseReadService.ListReadContext readContext = resolveListReadContext(
                boards,
                currentUser,
                precomputedPostCounts,
                precomputedSubscribedBoardIds);

        return boards.stream()
                .map(board -> buildResponse(
                        board,
                        readContext.subscriberCounts(),
                        readContext.postCounts(),
                        readContext.boardAdmins(),
                        readContext.subscribedBoardIds(),
                        currentUser))
                .toList();
    }

    private BoardResponseReadService.ListReadContext resolveListReadContext(
            List<Board> boards,
            User currentUser,
            Map<Long, Long> precomputedPostCounts,
            Set<Long> precomputedSubscribedBoardIds) {
        if (precomputedSubscribedBoardIds != null) {
            return boardResponseReadService.loadListWithSubscribedBoardIds(
                    boards,
                    currentUser,
                    precomputedSubscribedBoardIds);
        }
        if (precomputedPostCounts != null) {
            return boardResponseReadService.loadListWithPostCounts(boards, currentUser, precomputedPostCounts);
        }
        return boardResponseReadService.loadList(boards, currentUser);
    }

    List<AdminBoardResponse> assembleAdminAll(List<Board> boards) {
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }

        BoardResponseReadService.AdminReadContext readContext = boardResponseReadService.loadAdmin(boards);
        return boards.stream()
                .map(board -> buildAdminResponse(board, readContext))
                .toList();
    }

    private BoardListResponse buildResponse(Board board,
            Map<Long, Long> subscriberCounts,
            Map<Long, Long> postCounts,
            Map<Long, Admin> boardAdmins,
            Set<Long> subscribedBoardIds,
            User currentUser) {
        Long boardId = board.getBoardId();
        User adminUser = resolveAdminUser(board, boardAdmins.get(boardId));
        boolean isSubscribed = currentUser != null && subscribedBoardIds.contains(boardId);

        return new BoardListResponse(
                board,
                subscriberCounts.getOrDefault(boardId, 0L),
                postCounts.getOrDefault(boardId, 0L),
                adminUser.getDisplayName(),
                isSubscribed);
    }

    private AdminBoardResponse buildAdminResponse(Board board, BoardResponseReadService.AdminReadContext readContext) {
        User adminUser = resolveAdminUser(board, readContext.boardAdmins().get(board.getBoardId()));
        return new AdminBoardResponse(
                board,
                adminUser.getDisplayName(),
                adminUser.getUserId(),
                resolveGuidePrompt(board, readContext.guidePromptsByBoardId().get(board.getBoardId())));
    }

    private User resolveAdminUser(Board board, Admin boardAdmin) {
        return boardAdmin != null ? boardAdmin.getUser() : board.getCreator();
    }

    private String resolveGuidePrompt(Board board, String guidePrompt) {
        if (guidePrompt != null) {
            return guidePrompt;
        }
        return board.isAgentEnabled() ? normalizeDescription(board.getDescription()) : null;
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }
}
