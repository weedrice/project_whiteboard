package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
        long subscriberCount = boardSubscriptionRepository.countByBoard(board);
        User adminUser = adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board,
                        Role.BOARD_ADMIN, true)
                .map(Admin::getUser)
                .orElse(board.getCreator());

        boolean isAdmin = false;
        boolean isSubscribed = false;
        if (currentUser != null) {
            boolean isBoardAdmin = adminRepository.findByUserAndBoardAndIsActive(currentUser, board, true)
                    .isPresent();
            boolean isCreator = board.getCreator().getUserId().equals(currentUser.getUserId());

            isAdmin = currentUser.getIsSuperAdmin() || isBoardAdmin || isCreator;
            isSubscribed = boardSubscriptionRepository.existsByUserAndBoard(currentUser, board);
        }

        List<CategoryResponse> categories = boardCategoryRepository
                .findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)
                .stream()
                .map(CategoryResponse::new)
                .collect(Collectors.toList());

        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
        List<PostSummary> latestPosts = postService.getLatestPostsByBoard(board.getBoardId(), 15, currentUserId);

        return new BoardResponse(
                board,
                subscriberCount,
                adminUser.getDisplayName(),
                adminUser.getUserId(),
                isAdmin,
                isSubscribed,
                categories,
                latestPosts,
                board.isAgentEnabled(),
                isAdmin ? resolveGuidePrompt(board) : null);
    }

    private String resolveGuidePrompt(Board board) {
        return boardAiInfoRepository.findByBoard_BoardId(board.getBoardId())
                .map(BoardAiInfo::getGuidePrompt)
                .orElseGet(() -> board.isAgentEnabled() ? normalizeDescription(board.getDescription()) : null);
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }
}
