package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
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

    BoardResponse assemble(Board board, User currentUser) {
        return assembleAll(List.of(board), currentUser).stream()
                .findFirst()
                .orElseThrow();
    }

    List<BoardResponse> assembleAll(List<Board> boards, User currentUser) {
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }

        BoardResponseReadService.ReadContext readContext = boardResponseReadService.load(boards, currentUser);

        return boards.stream()
                .map(board -> buildResponse(
                        board,
                        readContext.subscriberCounts(),
                        readContext.boardAdmins(),
                        readContext.categoriesByBoardId(),
                        readContext.guidePromptsByBoardId(),
                        readContext.latestPostsByBoardId(),
                        readContext.adminBoardIds(),
                        readContext.subscribedBoardIds(),
                        currentUser))
                .toList();
    }

    private BoardResponse buildResponse(Board board,
            Map<Long, Long> subscriberCounts,
            Map<Long, Admin> boardAdmins,
            Map<Long, List<CategoryResponse>> categoriesByBoardId,
            Map<Long, String> guidePromptsByBoardId,
            Map<Long, List<PostSummary>> latestPostsByBoardId,
            Set<Long> adminBoardIds,
            Set<Long> subscribedBoardIds,
            User currentUser) {
        Long boardId = board.getBoardId();
        User adminUser = resolveAdminUser(board, boardAdmins.get(boardId));
        boolean isAdmin = currentUser != null && adminBoardIds.contains(boardId);
        boolean isSubscribed = currentUser != null && subscribedBoardIds.contains(boardId);

        return new BoardResponse(
                board,
                subscriberCounts.getOrDefault(boardId, 0L),
                adminUser.getDisplayName(),
                adminUser.getUserId(),
                isAdmin,
                isSubscribed,
                categoriesByBoardId.getOrDefault(boardId, Collections.emptyList()),
                latestPostsByBoardId.getOrDefault(boardId, Collections.emptyList()),
                board.isAgentEnabled(),
                isAdmin ? resolveGuidePrompt(board, guidePromptsByBoardId) : null);
    }

    private User resolveAdminUser(Board board, Admin boardAdmin) {
        return boardAdmin != null ? boardAdmin.getUser() : board.getCreator();
    }

    private String resolveGuidePrompt(Board board, Map<Long, String> guidePromptsByBoardId) {
        String guidePrompt = guidePromptsByBoardId.get(board.getBoardId());
        if (guidePrompt != null) {
            return guidePrompt;
        }
        return board.isAgentEnabled() ? normalizeDescription(board.getDescription()) : null;
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }
}
