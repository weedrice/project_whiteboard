package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class BoardResponseAssembler {

    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final AdminRepository adminRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostService postService;

    BoardResponse assemble(Board board, User currentUser) {
        return assembleAll(List.of(board), currentUser).stream()
                .findFirst()
                .orElseThrow();
    }

    List<BoardResponse> assembleAll(List<Board> boards, User currentUser) {
        if (boards == null || boards.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> boardIds = boards.stream()
                .map(Board::getBoardId)
                .toList();
        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;

        Map<Long, Long> subscriberCounts = boardSubscriptionRepository.countByBoardIds(boardIds).stream()
                .collect(Collectors.toMap(
                        BoardSubscriptionRepository.BoardSubscriberCountProjection::getBoardId,
                        BoardSubscriptionRepository.BoardSubscriberCountProjection::getSubscriberCount));
        Map<Long, Admin> boardAdmins = adminRepository
                .findByBoard_BoardIdInAndRoleAndIsActiveOrderByBoard_BoardIdAscAdminIdDesc(
                        boardIds, Role.BOARD_ADMIN, true)
                .stream()
                .collect(Collectors.toMap(
                        admin -> admin.getBoard().getBoardId(),
                        Function.identity(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new));
        Map<Long, List<CategoryResponse>> categoriesByBoardId = toCategoryMap(
                boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(boardIds, true));
        Map<Long, String> guidePromptsByBoardId = boardAiInfoRepository.findByBoard_BoardIdIn(boardIds).stream()
                .collect(Collectors.toMap(
                        boardAiInfo -> boardAiInfo.getBoard().getBoardId(),
                        BoardAiInfo::getGuidePrompt));

        Set<Long> adminBoardIds = resolveAdminBoardIds(currentUser, boards, boardIds);
        Set<Long> subscribedBoardIds = resolveSubscribedBoardIds(currentUser, boards);
        Map<Long, List<PostSummary>> latestPostsByBoardId = postService.getLatestPostsByBoards(
                boardIds,
                15,
                currentUserId,
                adminBoardIds);

        return boards.stream()
                .map(board -> buildResponse(
                        board,
                        subscriberCounts,
                        boardAdmins,
                        categoriesByBoardId,
                        guidePromptsByBoardId,
                        latestPostsByBoardId,
                        adminBoardIds,
                        subscribedBoardIds,
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

    private Set<Long> resolveAdminBoardIds(User currentUser, List<Board> boards, Collection<Long> boardIds) {
        if (currentUser == null) {
            return Collections.emptySet();
        }

        Set<Long> adminBoardIds = adminRepository.findByUserAndBoard_BoardIdInAndIsActive(currentUser, boardIds, true)
                .stream()
                .map(admin -> admin.getBoard().getBoardId())
                .collect(Collectors.toSet());
        for (Board board : boards) {
            if (Boolean.TRUE.equals(currentUser.getIsSuperAdmin())
                    || board.getCreator() != null && board.getCreator().getUserId().equals(currentUser.getUserId())) {
                adminBoardIds.add(board.getBoardId());
            }
        }
        return adminBoardIds;
    }

    private Set<Long> resolveSubscribedBoardIds(User currentUser, List<Board> boards) {
        if (currentUser == null) {
            return Collections.emptySet();
        }

        return boardSubscriptionRepository.findByUserAndBoardIn(currentUser, boards).stream()
                .map(subscription -> subscription.getBoard().getBoardId())
                .collect(Collectors.toSet());
    }

    private Map<Long, List<CategoryResponse>> toCategoryMap(List<BoardCategory> categories) {
        Map<Long, List<CategoryResponse>> categoriesByBoardId = new LinkedHashMap<>();
        for (BoardCategory category : categories) {
            categoriesByBoardId.computeIfAbsent(category.getBoard().getBoardId(), ignored -> new java.util.ArrayList<>())
                    .add(new CategoryResponse(category));
        }
        return categoriesByBoardId;
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
