package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.port.PostUserReadPort;
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

    private final PostUserReadPort postUserReadPort;
    private final AdminRepository adminRepository;

    PostReadContext resolve(Long currentUserId) {
        if (currentUserId == null) {
            return PostReadContext.anonymous();
        }
        ActorUserPrincipal viewer = postUserReadPort.resolve(currentUserId);
        return resolveForResolvedUser(viewer);
    }

    PostReadContext resolveForResolvedUser(ActorUserPrincipal viewer) {
        if (viewer == null || viewer.getUserId() == null) {
            return PostReadContext.anonymous();
        }
        Long currentUserId = viewer.getUserId();
        List<Long> blockedUserIds = postUserReadPort.getBlockedUserIdsForExistingUser(currentUserId);
        return new PostReadContext(viewer, currentUserId, blockedUserIds, toBlockedUserIdSet(blockedUserIds),
                Collections.emptySet());
    }

    PostReadContext resolveForExistingUser(Long currentUserId) {
        return resolve(currentUserId);
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
                postUserReadPort.getBlockedUserIds(currentUserId));
    }

    PostReadContext resolveForExistingUserPosts(Long currentUserId, Collection<Post> posts) {
        return withAdminBoardIdsForPosts(resolveForExistingUser(currentUserId), posts);
    }

    PostReadContext withAdminBoardIdsForPosts(PostReadContext context, Collection<Post> posts) {
        if (context == null || context.viewer() == null || posts == null || posts.isEmpty()
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

    private Set<Long> resolveActiveAdminBoardIds(ActorUserPrincipal viewer, Collection<Board> boards) {
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

        return Set.copyOf(adminRepository.findActiveBoardIdsByUserIdAndBoardIds(viewer.getUserId(), boardIds));
    }

    private Set<Long> resolveActiveAdminBoardIdsForPosts(ActorUserPrincipal viewer, Collection<Post> posts) {
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

        return Set.copyOf(adminRepository.findActiveBoardIdsByUserIdAndBoardIds(viewer.getUserId(), boardIds));
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

    private Set<Long> toBlockedUserIdSet(List<Long> blockedUserIds) {
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(blockedUserIds);
    }
}
