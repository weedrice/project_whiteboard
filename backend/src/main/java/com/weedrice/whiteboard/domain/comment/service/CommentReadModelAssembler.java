package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.badge.dto.BadgeCompactResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CommentReadModelAssembler {

    private static final String AUTHOR_TYPE_AGENT = "AGENT";
    private static final String AUTHOR_TYPE_USER = "USER";

    private final CommentReadSupport commentReadSupport;

    public CommentReadModel from(Comment comment, Set<Long> blockedUserIds) {
        return from(comment, blockedUserIds, Collections.emptyMap());
    }

    public CommentReadModel from(Comment comment, Set<Long> blockedUserIds, Map<Long, Long> replyCounts) {
        long replyCount = replyCounts.getOrDefault(comment.getCommentId(), 0L);
        if (commentReadSupport.isDeleted(comment)) {
            return new CommentReadModel(comment, CommentReadModel.Status.DELETED, null, null, replyCount);
        }
        if (Boolean.TRUE.equals(comment.getIsBlinded())) {
            return new CommentReadModel(comment, CommentReadModel.Status.BLINDED, activeAuthor(comment), null,
                    replyCount);
        }
        if (commentReadSupport.isBlockedAuthor(comment, blockedUserIds)) {
            return new CommentReadModel(
                    comment,
                    CommentReadModel.Status.BLOCKED_AUTHOR,
                    null,
                    comment.getUser().getUserId(),
                    replyCount);
        }
        return new CommentReadModel(comment, CommentReadModel.Status.ACTIVE, activeAuthor(comment), null, replyCount);
    }

    private CommentReadModel.Author activeAuthor(Comment comment) {
        if (comment.getUser() == null) {
            return null;
        }
        boolean agentAuthored = comment.getAgent() != null;
        return new CommentReadModel.Author(
                comment.getUser().getUserId(),
                agentAuthored ? comment.getAgent().getAgentId() : null,
                agentAuthored ? AUTHOR_TYPE_AGENT : AUTHOR_TYPE_USER,
                agentAuthored ? comment.getAgent().getName() : comment.getUser().getDisplayName(),
                agentAuthored ? null : comment.getUser().getProfileImageUrl(),
                agentAuthored ? null : representativeBadge(comment.getUser().getRepresentativeBadgeCode()));
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
