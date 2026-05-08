package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class PostReadContextResolver {

    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final AdminRepository adminRepository;

    PostReadContext resolve(Long currentUserId) {
        return resolve(currentUserId, false);
    }

    PostReadContext resolveForExistingUser(Long currentUserId) {
        return resolve(currentUserId, true);
    }

    private PostReadContext resolve(Long currentUserId, boolean useExistingUserBlockLookup) {
        if (currentUserId == null) {
            return PostReadContext.anonymous();
        }
        User viewer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = useExistingUserBlockLookup
                ? userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(currentUserId)
                : userBlockService.getBlockedUserIdsEitherDirection(currentUserId);
        Set<Long> blockedUserIdSet = blockedUserIds == null || blockedUserIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(blockedUserIds);
        return new PostReadContext(viewer, currentUserId, blockedUserIds, blockedUserIdSet, Collections.emptySet());
    }

    PostReadContext resolveForBoards(Long currentUserId, Collection<Board> boards) {
        return withAdminBoardIds(resolve(currentUserId), boards);
    }

    PostReadContext resolveQueryParameters(Long currentUserId) {
        if (currentUserId == null) {
            return PostReadContext.anonymous();
        }
        return PostReadContext.unresolvedUser(
                currentUserId,
                userBlockService.getBlockedUserIdsEitherDirection(currentUserId));
    }

    PostReadContext resolveForExistingUserPosts(Long currentUserId, Collection<Post> posts) {
        PostReadContext context = resolveForExistingUser(currentUserId);
        if (context.viewer() == null || posts == null || posts.isEmpty()
                || context.viewer().isUsableSuperAdmin()) {
            return context;
        }
        Set<Long> activeAdminBoardIds = resolveActiveAdminBoardIdsForPosts(context.viewer(), posts);
        return new PostReadContext(
                context.viewer(),
                context.currentUserId(),
                context.blockedUserIds(),
                context.blockedUserIdSet(),
                activeAdminBoardIds);
    }

    PostReadContext withAdminBoardIds(PostReadContext context, Collection<Board> boards) {
        if (context == null || context.viewer() == null || boards == null || boards.isEmpty()
                || context.viewer().isUsableSuperAdmin()) {
            return context;
        }
        Set<Long> activeAdminBoardIds = resolveActiveAdminBoardIds(context.viewer(), boards);
        return new PostReadContext(
                context.viewer(),
                context.currentUserId(),
                context.blockedUserIds(),
                context.blockedUserIdSet(),
                activeAdminBoardIds);
    }

    private Set<Long> resolveActiveAdminBoardIds(User viewer, Collection<Board> boards) {
        List<Long> boardIds = boards.stream()
                .filter(Objects::nonNull)
                .filter(this::requiresAdminAccess)
                .filter(board -> board.getBoardId() != null)
                .map(Board::getBoardId)
                .distinct()
                .toList();
        if (boardIds.isEmpty()) {
            return Collections.emptySet();
        }

        return adminRepository.findByUserAndBoard_BoardIdInAndIsActive(viewer, boardIds, true).stream()
                .map(Admin::getBoard)
                .filter(Objects::nonNull)
                .map(Board::getBoardId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Long> resolveActiveAdminBoardIdsForPosts(User viewer, Collection<Post> posts) {
        List<Long> boardIds = posts.stream()
                .filter(Objects::nonNull)
                .filter(this::requiresAdminAccess)
                .map(Post::getBoard)
                .filter(Objects::nonNull)
                .filter(board -> board.getBoardId() != null)
                .map(Board::getBoardId)
                .distinct()
                .toList();
        if (boardIds.isEmpty()) {
            return Collections.emptySet();
        }

        return adminRepository.findByUserAndBoard_BoardIdInAndIsActive(viewer, boardIds, true).stream()
                .map(Admin::getBoard)
                .filter(Objects::nonNull)
                .map(Board::getBoardId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private boolean requiresAdminAccess(Post post) {
        Board board = post.getBoard();
        return board != null
                && (!Boolean.TRUE.equals(board.getIsActive())
                || !Boolean.TRUE.equals(board.getIsPublic())
                || Boolean.TRUE.equals(post.getIsSecret()));
    }

    private boolean requiresAdminAccess(Board board) {
        return board != null
                && (!Boolean.TRUE.equals(board.getIsActive())
                || !Boolean.TRUE.equals(board.getIsPublic()));
    }
}
