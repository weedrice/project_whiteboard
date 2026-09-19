package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.badge.dto.BadgeCompactResponse;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CommentReadModelAssembler {

    private final CommentReadSupport commentReadSupport;

    public CommentReadModel from(Comment comment, AuthorSnapshot author, Set<Long> blockedUserIds) {
        return from(comment, author, blockedUserIds, Collections.emptyMap());
    }

    public CommentReadModel from(Comment comment, AuthorSnapshot author, Set<Long> blockedUserIds,
            Map<Long, Long> replyCounts) {
        long replyCount = replyCounts.getOrDefault(comment.getCommentId(), 0L);
        if (commentReadSupport.isDeleted(comment)) {
            return new CommentReadModel(comment, CommentReadModel.Status.DELETED, null, null, replyCount);
        }
        if (Boolean.TRUE.equals(comment.getIsBlinded())) {
            return new CommentReadModel(comment, CommentReadModel.Status.BLINDED, activeAuthor(author), null,
                    replyCount);
        }
        if (commentReadSupport.isBlockedAuthor(comment, blockedUserIds)) {
            return new CommentReadModel(
                    comment,
                    CommentReadModel.Status.BLOCKED_AUTHOR,
                    null,
                    comment.getUserId(),
                    replyCount);
        }
        return new CommentReadModel(comment, CommentReadModel.Status.ACTIVE, activeAuthor(author), null, replyCount);
    }

    private CommentReadModel.Author activeAuthor(AuthorSnapshot author) {
        if (author == null) {
            return null;
        }
        boolean agentAuthored = author.agentId() != null;
        return new CommentReadModel.Author(
                author.ownerUserId(),
                author.agentId(),
                author.authorType(),
                author.displayName(),
                agentAuthored ? null : author.profileImageUrl(),
                agentAuthored ? null : representativeBadge(author.representativeBadgeCode()));
    }

    private BadgeCompactResponse representativeBadge(String badgeCode) {
        if (badgeCode == null || badgeCode.isBlank()) {
            return null;
        }
        return BadgeCompactResponse.builder()
                .badgeCode(badgeCode)
                .build();
    }
}
