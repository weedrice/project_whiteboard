package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Shared source and batched audience checks for mention creation and delayed delivery. */
@Component
@RequiredArgsConstructor
class MentionNotificationReadPolicy {
    private final PostRepository posts;
    private final CommentRepository comments;
    private final UserBlockRepository blocks;
    private final AdminRepository admins;
    private final PostAccessPolicy postAccessPolicy;
    private final ContentAuthorVisibilityRepository authors;

    Post resolveSourcePost(NotificationSourceType type, Long sourceId, Long actorUserId) {
        if (sourceId == null || sourceId <= 0 || actorUserId == null) {
            return null;
        }
        if (type == NotificationSourceType.POST) {
            return posts.findByIdWithRelations(sourceId)
                    .filter(post -> Objects.equals(actorUserId, post.getUserId())).orElse(null);
        }
        if (type != NotificationSourceType.COMMENT) {
            return null;
        }
        return comments.findNonDeletedByIdWithRelations(sourceId)
                .filter(comment -> !Boolean.TRUE.equals(comment.getIsDeleted()) && !Boolean.TRUE.equals(comment.getIsBlinded()))
                .filter(comment -> Objects.equals(actorUserId, comment.getUserId()))
                .flatMap(comment -> posts.findByIdWithRelations(comment.getPostId())).orElse(null);
    }

    List<User> readableRecipients(Post post, Long actorUserId, List<User> recipients) {
        if (post == null || actorUserId == null) {
            return List.of();
        }
        List<User> candidates = recipients.stream()
                .filter(user -> User.STATUS_ACTIVE.equals(user.getStatus()) && user.getDeletedAt() == null)
                .filter(user -> !Objects.equals(actorUserId, user.getUserId())).toList();
        if (candidates.isEmpty() || !authors.findVisibleUserIds(List.of(actorUserId)).contains(actorUserId)) {
            return List.of();
        }
        List<Long> ids = candidates.stream().map(User::getUserId).toList();
        Set<Long> blockedIds = new HashSet<>(blocks.findBlockedCandidateUserIdsEitherDirection(actorUserId, ids));
        if (post.getUserId() != null && !Objects.equals(actorUserId, post.getUserId())) {
            blockedIds.addAll(blocks.findBlockedCandidateUserIdsEitherDirection(post.getUserId(), ids));
        }
        Long boardId = post.getBoard() == null ? null : post.getBoard().getBoardId();
        Set<Long> managers = boardId == null ? Set.of()
                : Set.copyOf(admins.findActiveUserIdsByBoardIdAndUserIds(boardId, ids));
        // Parent-author BAN is intentionally not a direct-read denial. PostAccessPolicy retains owner/admin exceptions.
        return candidates.stream()
                .filter(user -> !blockedIds.contains(user.getUserId()))
                .filter(user -> postAccessPolicy.isReadable(post, user, false,
                        managers.contains(user.getUserId()) ? Set.of(boardId) : Set.of()))
                .toList();
    }
}
