package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostLatestReadService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class BoardResponseReadService {

    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final AdminRepository adminRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardAiInfoRepository boardAiInfoRepository;
    private final PostRepository postRepository;
    private final PostLatestReadService postLatestReadService;

    ListReadContext loadList(List<Board> boards, User currentUser) {
        if (boards == null || boards.isEmpty()) {
            return ListReadContext.empty();
        }

        List<Long> boardIds = boards.stream()
                .map(Board::getBoardId)
                .toList();

        Map<Long, Long> subscriberCounts = boardSubscriptionRepository.countByBoardIds(boardIds).stream()
                .collect(Collectors.toMap(
                        BoardSubscriptionRepository.BoardSubscriberCountProjection::getBoardId,
                        BoardSubscriptionRepository.BoardSubscriberCountProjection::getSubscriberCount));
        Map<Long, Long> postCounts = postRepository.countActiveByBoardIds(boardIds).stream()
                .collect(Collectors.toMap(
                        PostRepository.BoardPostCountProjection::getBoardId,
                        PostRepository.BoardPostCountProjection::getPostCount));
        Map<Long, Admin> boardAdmins = resolveBoardAdmins(boardIds);
        Set<Long> adminBoardIds = resolveAdminBoardIds(currentUser, boards, boardIds);
        Set<Long> subscribedBoardIds = resolveSubscribedBoardIds(currentUser, boards);
        return new ListReadContext(
                subscriberCounts,
                postCounts,
                boardAdmins,
                adminBoardIds,
                subscribedBoardIds);
    }

    DetailReadContext loadDetail(Board board, User currentUser) {
        ListReadContext listReadContext = loadList(List.of(board), currentUser);
        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
        Long boardId = board.getBoardId();

        List<CategoryResponse> categories = boardCategoryRepository
                .findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(boardId, true)
                .stream()
                .map(CategoryResponse::new)
                .toList();
        Set<Long> adminBoardIds = listReadContext.adminBoardIds();
        List<PostSummary> latestPosts = postLatestReadService.getLatestPostsByBoards(
                List.of(boardId),
                15,
                currentUserId,
                adminBoardIds)
                .getOrDefault(boardId, Collections.emptyList());
        String guidePrompt = boardAiInfoRepository.findByBoard_BoardIdIn(List.of(boardId)).stream()
                .findFirst()
                .map(BoardAiInfo::getGuidePrompt)
                .orElse(null);

        return new DetailReadContext(
                listReadContext,
                categories,
                latestPosts,
                guidePrompt);
    }

    AdminReadContext loadAdmin(List<Board> boards) {
        if (boards == null || boards.isEmpty()) {
            return AdminReadContext.empty();
        }
        List<Long> boardIds = boards.stream()
                .map(Board::getBoardId)
                .toList();
        Map<Long, Admin> boardAdmins = resolveBoardAdmins(boardIds);
        Map<Long, String> guidePromptsByBoardId = boardAiInfoRepository.findByBoard_BoardIdIn(boardIds).stream()
                .collect(Collectors.toMap(
                        boardAiInfo -> boardAiInfo.getBoard().getBoardId(),
                        BoardAiInfo::getGuidePrompt));
        return new AdminReadContext(boardAdmins, guidePromptsByBoardId);
    }

    private Map<Long, Admin> resolveBoardAdmins(Collection<Long> boardIds) {
        return adminRepository
                .findByBoard_BoardIdInAndRoleAndIsActiveOrderByBoard_BoardIdAscAdminIdDesc(
                        boardIds, Role.BOARD_ADMIN, true)
                .stream()
                .collect(Collectors.toMap(
                        admin -> admin.getBoard().getBoardId(),
                        Function.identity(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new));
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

    record ListReadContext(
            Map<Long, Long> subscriberCounts,
            Map<Long, Long> postCounts,
            Map<Long, Admin> boardAdmins,
            Set<Long> adminBoardIds,
            Set<Long> subscribedBoardIds) {

        private static ListReadContext empty() {
            return new ListReadContext(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptySet(),
                    Collections.emptySet());
        }
    }

    record DetailReadContext(
            ListReadContext listReadContext,
            List<CategoryResponse> categories,
            List<PostSummary> latestPosts,
            String guidePrompt) {
    }

    record AdminReadContext(
            Map<Long, Admin> boardAdmins,
            Map<Long, String> guidePromptsByBoardId) {

        private static AdminReadContext empty() {
            return new AdminReadContext(Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
